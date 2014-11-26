package ca.bc.gov.open.cpf.api.scheduler;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;

public class BatchJobRequestExecutionGroup {
  private final long batchJobId;

  private final BusinessApplication businessApplication;

  private final Map<String, String> businessApplicationParameterMap;

  private long executionStartTime;

  private String id;

  private final AtomicInteger attempt = new AtomicInteger(0);

  private final String resultDataContentType;

  private final Timestamp scheduleTimestamp;

  private final String consumerKey;

  private final String moduleName;

  private long moduleStartTime;

  private int numCompletedRequests;

  private int numFailedRequests;

  private boolean cancelled = false;

  private final long sequenceNumber;

  private final String baseId;

  public BatchJobRequestExecutionGroup(final String consumerKey,
    final long batchJobId, final BusinessApplication businessApplication,
    final Map<String, String> businessApplicationParameterMap,
    final String resultDataContentType, final Timestamp scheduleTimestamp,
    final long sequenceNumber) {
    this.consumerKey = consumerKey;
    this.batchJobId = batchJobId;
    this.businessApplication = businessApplication;
    this.moduleName = businessApplication.getModule().getName();
    this.businessApplicationParameterMap = businessApplicationParameterMap;
    this.resultDataContentType = resultDataContentType;
    this.scheduleTimestamp = scheduleTimestamp;
    this.sequenceNumber = sequenceNumber;
    this.baseId = batchJobId + "-" + sequenceNumber;
    resetId();
  }

  public void cancel() {
    this.cancelled = true;
  }

  public String getBaseId() {
    return this.baseId;
  }

  public long getBatchJobId() {
    return this.batchJobId;
  }

  public BusinessApplication getBusinessApplication() {
    return this.businessApplication;
  }

  public String getBusinessApplicationName() {
    return this.businessApplication.getName();
  }

  public Map<String, String> getBusinessApplicationParameterMap() {
    return this.businessApplicationParameterMap;
  }

  public String getconsumerKey() {
    return this.consumerKey;
  }

  public long getExecutionStartTime() {
    return this.executionStartTime;
  }

  public String getId() {
    return this.id;
  }

  public Module getModule() {
    return this.businessApplication.getModule();
  }

  public String getModuleName() {
    return this.moduleName;
  }

  public long getModuleStartTime() {
    return this.moduleStartTime;
  }

  public int getNumCompletedRequests() {
    return this.numCompletedRequests;
  }

  public int getNumFailedRequests() {
    return this.numFailedRequests;
  }

  public String getResultDataContentType() {
    return this.resultDataContentType;
  }

  public Timestamp getScheduleTimestamp() {
    return this.scheduleTimestamp;
  }

  public long getSequenceNumber() {
    return this.sequenceNumber;
  }

  public Timestamp getStartedTimestamp() {
    return this.scheduleTimestamp;
  }

  public boolean isCancelled() {
    return this.cancelled;
  }

  public void resetId() {
    this.id = this.batchJobId + "-" + this.sequenceNumber + "-"
      + this.attempt.incrementAndGet();
  }

  public void setExecutionStartTime(final long executionStartTime) {
    this.executionStartTime = executionStartTime;
  }

  public void setModuleStartTime(final long moduleStartTime) {
    this.moduleStartTime = moduleStartTime;
  }

  public void setNumCompletedRequests(final int numCompletedRequests) {
    this.numCompletedRequests = numCompletedRequests;
  }

  public void setNumFailedRequests(final int numFailedRequests) {
    this.numFailedRequests = numFailedRequests;
  }

  @Override
  public String toString() {
    return this.id;
  }
}
