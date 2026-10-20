package org.chenile.orchestrator.process.test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

public abstract class SharedData {
  public static Map<String, CountDownLatch> LATCHES =
      new java.util.concurrent.ConcurrentHashMap<>();
  public static boolean SYNCH_MODE = true;
}
