package org.chenile.orchestrator.process.test;

import org.chenile.orchestrator.process.configuration.dao.ProcessRepository;
import org.chenile.orchestrator.process.model.Constants;
import org.chenile.orchestrator.process.model.Process;
import org.chenile.workflow.api.StateEntityService;
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
        assertState(process.id,Constants.PROCESSED_STATE);
        String fileId = process.id + "FILE1";
        assertState(fileId,Constants.PROCESSED_STATE);
        assertState(fileId + "CHUNK1",Constants.PROCESSED_STATE);

        // Make sure that the all successor processes are created and have been successfully processed.
        List<Process> allPredecessorList= processRepository.findByPredecessorIdIsNotNull();
        for(Process p: allPredecessorList){
            assertState(p.getId(),Constants.PROCESSED_STATE);
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
        assertState(process.id,Constants.PROCESSED_STATE);
        // Is FILE1 processed with the correct args?
        String fileId = process.id + "FILE1";
        String expectedArgs = """
                { "filename" : "file1" }
                """;
        assertStateArgs(fileId,Constants.PROCESSED_STATE,expectedArgs);
        assertState(fileId + "CHUNK1",Constants.PROCESSED_STATE);
        // Test if the second file has been processed successfully as well.
        fileId = process.id + "FILE2";
        expectedArgs = """
                { "filename" : "file2" }
                """;
        assertStateArgs(fileId,Constants.PROCESSED_STATE,expectedArgs);
        assertState(fileId + "CHUNK1",Constants.PROCESSED_STATE);
        // Make sure that the successor process is created and have been successfully processed.
        List<Process> allPredecessorList= processRepository.findByPredecessorIdIsNotNull();
        for(Process p: allPredecessorList){
            assertState(p.getId(),Constants.PROCESSED_STATE);
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
        assertState(process.id,Constants.SPLIT_PENDING_STATE);
        unblock("FEED1-SPLITTER");
        assertState(process.id,Constants.SUB_PROCESSES_PENDING_STATE);
        unblock("FEED1FILE1-SPLITTER");
        unblock("FEED1FILE1CHUNK1-EXECUTOR");
        unblock("FEED1FILE1-AGGREGATOR");
        unblock("FEED1FILE1fileSuccessor-EXECUTOR");
        assertState("FEED1FILE1fileSuccessor",Constants.PROCESSED_STATE);
        assertState(process.id,Constants.AGGREGATION_PENDING_STATE);
        unblock("FEED1-AGGREGATOR");
        assertState(process.id,Constants.PROCESSED_STATE);
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
        assertState(process.id,Constants.SPLIT_PENDING_STATE);
        unblock("FEED1-SPLITTER");
        assertState(process.id,Constants.SUB_PROCESSES_PENDING_STATE);
        unblock("FEED1FILE1-SPLITTER");
        unblock("FEED1FILE2-SPLITTER");
        unblock("FEED1FILE1CHUNK1-EXECUTOR");
        unblock("FEED1FILE2CHUNK1-EXECUTOR");
        unblock("FEED1FILE1-AGGREGATOR");
        unblock("FEED1FILE2-AGGREGATOR");
        unblock("FEED1FILE1fileSuccessor-EXECUTOR");
        unblock("FEED1FILE2fileSuccessor-EXECUTOR");
        System.out.println("Done with all the count down LATCHES");
        assertState("FEED1FILE1fileSuccessor",Constants.PROCESSED_STATE);
        assertState(process.id,Constants.AGGREGATION_PENDING_STATE);
        unblock("FEED1-AGGREGATOR");
        assertState(process.id,Constants.PROCESSED_STATE);
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
    private void unblock(String latchKey) throws Exception{
        String proceedKey = latchKey + "PROCEED";
        CountDownLatch proceedLatch = new CountDownLatch(1);
        LATCHES.put(proceedKey,proceedLatch);
        LATCHES.get(latchKey).countDown();
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
