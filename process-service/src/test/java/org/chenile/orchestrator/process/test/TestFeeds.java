package org.chenile.orchestrator.process.test;

import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.workflow.api.StateEntityService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import static org.chenile.orchestrator.process.test.SharedData.*;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = SpringConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("unittest")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD)
public class TestFeeds {
    @Autowired
    StateEntityService<Process> processManager;

    @Autowired
    private ProcessRepository processRepository;

    @Test
    @Order(10)
    public void testWith1File() throws Exception {
        synch();
        FeedSplitter.numFiles = 1;
        Process process = new Process();
        process.processType = "feed";
        process.id = "FEED1";
        processManager.create(process);
        assertState(process.id,Constants.States.PROCESSED);
        String fileId = process.id + "FILE1";
        assertState(fileId,Constants.States.PROCESSED);
        assertState(fileId + "CHUNK1",Constants.States.PROCESSED);

        // Make sure that the all successor processes are created and have been successfully processed.
        List<Process> allPredecessorList= processRepository.findByPredecessorIdIsNotNull();
        for(Process p: allPredecessorList){
            assertState(p.getId(),Constants.States.PROCESSED);
        }
    }

    @Test
    @Order(9)
    public void testWith2Files() throws Exception {
        synch();
        FeedSplitter.numFiles = 2;
        Process process = new Process();
        process.processType = "feed";
        process.id = "FEED2";
        processManager.create(process);
        assertState(process.id,Constants.States.PROCESSED);
        // Is FILE1 processed with the correct args?
        String fileId = process.id + "FILE1";
        String expectedArgs = """
                { "filename" : "file1" }
                """;
        assertStateArgs(fileId,Constants.States.PROCESSED,expectedArgs);
        assertState(fileId + "CHUNK1",Constants.States.PROCESSED);
        // Test if the second file has been processed successfully as well.
        fileId = process.id + "FILE2";
        expectedArgs = """
                { "filename" : "file2" }
                """;
        assertStateArgs(fileId,Constants.States.PROCESSED,expectedArgs);
        assertState(fileId + "CHUNK1",Constants.States.PROCESSED);
        // Make sure that the successor process is created and have been successfully processed.
        List<Process> allPredecessorList= processRepository.findByPredecessorIdIsNotNull();
        for(Process p: allPredecessorList){
            assertState(p.getId(),Constants.States.PROCESSED);
        }
    }

    @Test
    @Order(8)
    public void testWith1FileAsynch() throws Exception {
        asynch();
        FeedSplitter.numFiles = 1;
        Process process = new Process();
        process.processType = "feed";
        process.id = "FEED1";
        processManager.create(process);
        assertState(process.id,Constants.States.SPLITTING_AND_WAITING_SUBPROCESSES);
        unblock("FEED1-SPLITTER");
        assertState(process.id,Constants.States.SPLITTING_AND_WAITING_SUBPROCESSES);
        unblock("FEED1FILE1-SPLITTER");
        assertState("FEED1FILE1fileSuccessor",Constants.States.DORMANT);
        unblock("FEED1FILE1CHUNK1-EXECUTOR");
        unblock("FEED1FILE1-AGGREGATOR");
        assertState("FEED1FILE1fileSuccessor",Constants.States.EXECUTING);
        unblock("FEED1FILE1fileSuccessor-EXECUTOR");
        assertState("FEED1FILE1fileSuccessor",Constants.States.PROCESSED);
        assertState(process.id,Constants.States.AGGREGATION_PENDING);
        unblock("FEED1-AGGREGATOR");
        assertState(process.id,Constants.States.PROCESSED);
    }

    @Test
    @Order(7)
    public void testWith2FilesAsynch() throws Exception {
        asynch();
        FeedSplitter.numFiles = 2;
        Process process = new Process();
        process.processType = "feed";
        process.id = "FEED1";
        processManager.create(process);
        assertState(process.id,Constants.States.SPLITTING_AND_WAITING_SUBPROCESSES);
        unblock("FEED1-SPLITTER");
        assertState(process.id,Constants.States.SPLITTING_AND_WAITING_SUBPROCESSES);
        unblock("FEED1FILE1-SPLITTER");
        unblock("FEED1FILE2-SPLITTER");
        unblock("FEED1FILE1CHUNK1-EXECUTOR");
        unblock("FEED1FILE2CHUNK1-EXECUTOR");
        unblock("FEED1FILE1-AGGREGATOR");
        unblock("FEED1FILE2-AGGREGATOR");
        unblock("FEED1FILE1fileSuccessor-EXECUTOR");
        unblock("FEED1FILE2fileSuccessor-EXECUTOR");
        System.out.println("Done with all the count down LATCHES");
        assertState("FEED1FILE1fileSuccessor",Constants.States.PROCESSED);
        assertState(process.id,Constants.States.AGGREGATION_PENDING);
        unblock("FEED1-AGGREGATOR");
        assertState(process.id,Constants.States.PROCESSED);
    }

    @Test
    @Order(11)
    public void testWith2FilesBatch() throws Exception {
        synch();
        int numTestFiles = 3;
        FeedSplitter.numFiles = numTestFiles;
        FeedSplitter.batchSize = 1;
        Process process = new Process();
        process.processType = "feed";
        process.id = "FEED2";
        processManager.create(process);
        assertState(process.id,Constants.States.PROCESSED);

        for (int i = 1; i <= numTestFiles; i++) {
            String fileId = process.id + "FILE" + i;
            String chunkId = fileId + "CHUNK1";
            String expectedArgs = """
                { "filename" : "file%d" }
                """.formatted(i);

            System.out.println("Verifying process: " + fileId);
            assertStateArgs(fileId, Constants.States.PROCESSED, expectedArgs);
            assertState(chunkId, Constants.States.PROCESSED);
        }
        // Make sure that the successor process is created and have been successfully processed.
        List<Process> allPredecessorList= processRepository.findByPredecessorIdIsNotNull();
        for(Process p: allPredecessorList){
            assertState(p.getId(),Constants.States.PROCESSED);
        }
    }

    @Test
    @Order(12)
    public void testWithMultipleFilesAsynch() throws Exception {
        // --- SETUP ---
        asynch();
        int numTestFiles = 3;
        FeedSplitter.numFiles = numTestFiles;
        FeedSplitter.batchSize = 1;

        Process process = new Process();
        process.processType = "feed";
        process.id = "FEED";
        processManager.create(process);

        assertState(process.id, Constants.States.SPLITTING_AND_WAITING_SUBPROCESSES);
        unblock(process.id + "-SPLITTER");
        assertState(process.id, Constants.States.SPLITTING_AND_WAITING_SUBPROCESSES);


        // --- ADD THE VERIFICATION CODE HERE ---
        System.out.println("--- VERIFYING DATABASE SAVE ---");
        Process savedProcess = processRepository.findById(process.getId()).orElse(null);

        //System.out.println("VERIFICATION: Initialized states are: " + savedProcess.initializedStates + "--> desc=="+ savedProcess.description);

        System.out.println("--- VERIFICATION SUCCESSFUL ---");
        // --- END OF VERIFICATION CODE ---

        // Now, loop to unblock each of the 'file' sub-processes and their children
        for (int i = 1; i <= numTestFiles; i++) {
            String fileId = process.id + "FILE" + i;
            System.out.println("Unblocking flow for: " + fileId);

            // Unblock the file's own splitter (to create the chunk)
            unblock(fileId + "-SPLITTER");
            // Unblock the chunk's executor
            unblock(fileId + "CHUNK1-EXECUTOR");
            // Unblock the file's aggregator
            unblock(fileId + "-AGGREGATOR");
            // Unblock the file's successor's executor
            unblock(fileId + "fileSuccessor-EXECUTOR");
        }

        // After all individual file flows are unblocked, check the final states
        System.out.println("Done with all the count down LATCHES");

        // Verify a sample successor is processed
        assertState(process.id + "FILE1fileSuccessor", Constants.States.PROCESSED);

        // Check that the main parent process has moved to aggregation
        assertState(process.id, Constants.States.AGGREGATION_PENDING);

        // Unblock the final aggregator to finish the whole process
        unblock(process.id + "-AGGREGATOR");
        assertState(process.id, Constants.States.PROCESSED);
    }


    @After
    public void cleanupSharedState() {
        synch();
        FeedSplitter.numFiles = 1;
       // FeedSplitter.batchSize = 10; // Or whatever your default is
    }

    private void synch(){
        SYNCH_MODE = true;
    }

    private void asynch(){
        SYNCH_MODE = false;
        LATCHES = new HashMap<>();
    }

    /**
     * This method co-ordinates with InVMWorkerStarterDelegator to make sure that the
     * tests are predictable.<br/>
     * The INVMWorkerStarterDelegator class waits for a particular count down latch at any time.
     * This class counts the latch down so that the thread can progress. The thread then counts the
     * latch down so that the test can proceed.
     * @param latchKey - the latch key that the InVMWorkerStarterDelegator is waiting on.
     * @throws Exception - for any exception in await below
     */
    private void unblock_old(String latchKey) throws Exception{
        String proceedKey = latchKey + "PROCEED";
        CountDownLatch proceedLatch = new CountDownLatch(1);
        LATCHES.put(proceedKey,proceedLatch);
        LATCHES.get(latchKey).countDown();
        proceedLatch.await(1000L, TimeUnit.MILLISECONDS);
    }

    private void unblock(String latchKey) throws Exception {
        String proceedKey = latchKey + "PROCEED";
        CountDownLatch proceedLatch = new CountDownLatch(1);
        LATCHES.put(proceedKey, proceedLatch);

        // Wait intelligently for the latch to be created
        CountDownLatch workerLatch = null;
        long timeout = System.currentTimeMillis() + 2000; // Max wait 2 seconds
        while (System.currentTimeMillis() < timeout) {
            workerLatch = LATCHES.get(latchKey);
            if (workerLatch != null) break; // Found it!
            Thread.sleep(50); // Small pause before retrying
        }

        if (workerLatch == null) {
            throw new TimeoutException("Timed out waiting for latch: " + latchKey);
        }

        workerLatch.countDown();
        proceedLatch.await(1000L, TimeUnit.MILLISECONDS);
    }

    private void assertState(String processId,String stateId){
        Process p = processManager.retrieve(processId).getMutatedEntity();
        Assert.assertEquals(stateId,p.getCurrentState().getStateId());
    }

    private void assertStateArgs(String processId,String stateId,String args){
        Process p = processManager.retrieve(processId).getMutatedEntity();
        Assert.assertEquals(stateId,p.getCurrentState().getStateId());
        Assert.assertEquals(args,p.args);
    }

}
