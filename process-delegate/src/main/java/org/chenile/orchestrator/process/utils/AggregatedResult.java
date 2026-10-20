package org.chenile.orchestrator.process.utils;

import java.util.ArrayList;
import java.util.List;

/** Typed result produced by aggregating the outputs and errors of child processes. */
public class AggregatedResult<ChildOutput> {
  public int completedChildren;
  public int failedChildren;
  public List<ChildOutput> outputs = new ArrayList<>();
  public List<String> errors = new ArrayList<>();
}
