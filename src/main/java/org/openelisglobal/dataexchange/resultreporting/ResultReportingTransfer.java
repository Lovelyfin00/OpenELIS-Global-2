/**
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is OpenELIS code.
 *
 * Copyright (C) CIRG, University of Washington, Seattle WA.  All Rights Reserved.
 *
 */
package org.openelisglobal.dataexchange.resultreporting;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.Task.TaskOutputComponent;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.ExternalOrderStatus;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.dataexchange.aggregatereporting.valueholder.ReportExternalExport;
import org.openelisglobal.dataexchange.aggregatereporting.valueholder.ReportQueueType;
import org.openelisglobal.dataexchange.common.ITransmissionResponseHandler;
import org.openelisglobal.dataexchange.common.ReportTransmission;
import org.openelisglobal.dataexchange.order.valueholder.ElectronicOrder;
import org.openelisglobal.dataexchange.orderresult.OrderResponseWorker.Event;
import org.openelisglobal.dataexchange.orderresult.valueholder.HL7MessageOut;
import org.openelisglobal.dataexchange.resultreporting.beans.ResultReportXmit;
import org.openelisglobal.dataexchange.service.aggregatereporting.ReportExternalExportService;
import org.openelisglobal.dataexchange.service.aggregatereporting.ReportQueueTypeService;
import org.openelisglobal.dataexchange.service.order.ElectronicOrderService;
import org.openelisglobal.dataexchange.service.orderresult.HL7MessageOutService;
import org.openelisglobal.referencetables.service.ReferenceTablesService;
import org.openelisglobal.referencetables.valueholder.ReferenceTables;
import org.openelisglobal.reports.service.DocumentTrackService;
import org.openelisglobal.reports.service.DocumentTypeService;
import org.openelisglobal.reports.valueholder.DocumentTrack;
import org.openelisglobal.reports.valueholder.DocumentType;
import org.openelisglobal.result.valueholder.Result;
import org.openelisglobal.spring.util.SpringContext;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.TokenClientParam;

public class ResultReportingTransfer {

    private FhirContext fhirContext = SpringContext.getBean(FhirContext.class);

    private Task task = null;
    private ServiceRequest serviceRequest = null;
    private Patient patient = null;
    protected ElectronicOrderService electronicOrderService = SpringContext.getBean(ElectronicOrderService.class);

    private static DocumentType DOCUMENT_TYPE;
    private static String QUEUE_TYPE_ID;
    private static String RESULT_REFERRANCE_TABLE_ID;

    static {
        DOCUMENT_TYPE = SpringContext.getBean(DocumentTypeService.class).getDocumentTypeByName("resultExport");
        ReferenceTables referenceTable = SpringContext.getBean(ReferenceTablesService.class)
                .getReferenceTableByName("RESULT");
        if (referenceTable != null) {
            RESULT_REFERRANCE_TABLE_ID = referenceTable.getId();
        }
        ReportQueueType queueType = SpringContext.getBean(ReportQueueTypeService.class)
                .getReportQueueTypeByName("Results");
        if (queueType != null) {
            QUEUE_TYPE_ID = queueType.getId();
        }
    }

    public void sendResults(ResultReportXmit resultReport, List<Result> reportingResult, String url) {

        if (resultReport.getTestResults() == null || resultReport.getTestResults().isEmpty()) {
            return;
        }

        if (resultReport.getTestResults().get(0).getReferringOrderNumber().isEmpty()) {
            ITransmissionResponseHandler responseHandler = new ResultFailHandler(reportingResult);
            new ReportTransmission().sendHL7Report(resultReport, url, responseHandler);
        } else { // FHIR

            String orderNumber = resultReport.getTestResults().get(0).getReferringOrderNumber();
            List<ElectronicOrder> eOrders = electronicOrderService.getElectronicOrdersByExternalId(orderNumber);
            ElectronicOrder eOrder = eOrders.get(eOrders.size() - 1);
            ExternalOrderStatus eOrderStatus = SpringContext.getBean(IStatusService.class)
                    .getExternalOrderStatusForID(eOrder.getStatusId());

            IGenericClient localFhirClient = fhirContext
                    .newRestfulGenericClient("https://host.openelis.org:8444/hapi-fhir-jpaserver/fhir/");

            task = fhirContext.newJsonParser().parseResource(Task.class, eOrder.getData());
            System.out.println("task: " + fhirContext.newJsonParser().encodeResourceToString(task));

            Bundle srBundle = (Bundle) localFhirClient.search().forResource(ServiceRequest.class)
                    .where(new TokenClientParam("identifier").exactly()
                            .code(task.getBasedOn().get(0).getReferenceElement().getIdPart()))
                    .prettyPrint().execute();

            serviceRequest = null;
            for (BundleEntryComponent bundleComponent : srBundle.getEntry()) {
                if (bundleComponent.hasResource()
                        && ResourceType.ServiceRequest.equals(bundleComponent.getResource().getResourceType())) {
                    serviceRequest = (ServiceRequest) bundleComponent.getResource();
                }
            }

            System.out.println("serviceRequest: " + fhirContext.newJsonParser().encodeResourceToString(serviceRequest));
            serviceRequest.getSubject().getReferenceElement().getIdPart();

            Bundle pBundle = (Bundle) localFhirClient.search().forResource(Patient.class)
                    .where(new TokenClientParam("identifier").exactly()
                            .code(serviceRequest.getSubject().getReferenceElement().getIdPart()))
                    .prettyPrint().execute();

            patient = null;
            for (BundleEntryComponent bundleComponent : pBundle.getEntry()) {
                if (bundleComponent.hasResource()
                        && ResourceType.Patient.equals(bundleComponent.getResource().getResourceType())) {
                    patient = (Patient) bundleComponent.getResource();
                }
            }

            System.out.println("patient: " + fhirContext.newJsonParser().encodeResourceToString(patient));

            Observation observation = new Observation();
            // observation.setId("f001");
            observation.setIdentifier(serviceRequest.getIdentifier());
            observation.setBasedOn(serviceRequest.getBasedOn());
            observation.setStatus(Observation.ObservationStatus.FINAL);
            observation.setCode(serviceRequest.getCode());
            observation.setSubject(serviceRequest.getSubject());
            Quantity quantity = new Quantity();
            quantity.setValue(new java.math.BigDecimal(
                    resultReport.getTestResults().get(0).getResults().get(0).getResult().getText()));
            quantity.setUnit(resultReport.getTestResults().get(0).getUnits());
            observation.setValue(quantity);
            MethodOutcome oOutcome = localFhirClient.create().resource(observation).execute();

            DiagnosticReport diagnosticReport = new DiagnosticReport();
            diagnosticReport.setId(resultReport.getTestResults().get(0).getTest().getCode());
            diagnosticReport.setIdentifier(serviceRequest.getIdentifier());
            diagnosticReport.setBasedOn(serviceRequest.getBasedOn());
            diagnosticReport.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
            diagnosticReport.setCode(serviceRequest.getCode());
            diagnosticReport.setSubject(serviceRequest.getSubject());

            Reference observationReference = new Reference();
            observationReference.setId(oOutcome.getId().toString());
            diagnosticReport.addResult(observationReference);

            MethodOutcome drOutcome = localFhirClient.create().resource(diagnosticReport).execute();
            System.out.println("observation: " + oOutcome.getId());
            System.out.println("diagnosticReport: " + drOutcome.getId());
            System.out.println("task: " + task.getId());

            Reference diagnosticReportReference = new Reference();
            diagnosticReportReference.setId(drOutcome.getId().toString());

            TaskOutputComponent theOutputComponent = new TaskOutputComponent();
            theOutputComponent.setValue(diagnosticReportReference);

//          List<TaskOutputComponent> theOutputList = null;
//          theOutputList.add(theOutputComponent);

            task.addOutput(theOutputComponent);
            
            System.out.println("task: " + fhirContext.newJsonParser().encodeResourceToString(task));
            System.out.println("task: " + task.getOutput().get(0).getValue().getId().toString());
            
            try {
            MethodOutcome tOutcome = localFhirClient.update(task.getId(), task);
            MethodOutcome t1Outcome = 
                    localFhirClient.update()
                    .resource(fhirContext.newJsonParser().encodeResourceToString(task))
                    .execute();
            
                localFhirClient.update().resource(task).execute();
            } catch (Exception e){
                System.out.println("task update exception: "  + e.getStackTrace());
            }
        }
    }

    class ResultFailHandler implements ITransmissionResponseHandler {

        private List<Result> reportingResults;

        public ResultFailHandler(List<Result> reportingResults) {
            this.reportingResults = reportingResults;
        }

        @Override
        public void handleResponse(int httpReturnStatus, List<String> errors, String msg) {
            if (httpReturnStatus == HttpServletResponse.SC_OK) {
                markFinalResultsAsSent();
                persistMessage(msg, true);
            } else {
                bufferResults(msg);
                persistMessage(msg, false);
            }
        }

        private void persistMessage(String msg, boolean success) {
            HL7MessageOutService messageOutService = SpringContext.getBean(HL7MessageOutService.class);
            HL7MessageOut messageOut = new HL7MessageOut();
            messageOut.setData(msg);
            if (success) {
                messageOut.setStatus(HL7MessageOut.SUCCESS);
            } else {
                messageOut.setStatus(HL7MessageOut.FAIL);
            }
            messageOutService.insert(messageOut);
        }

        private void bufferResults(String msg) {
            ReportExternalExport report = new ReportExternalExport();
            report.setData(msg);
            report.setSysUserId("1");
            report.setEventDate(DateUtil.getNowAsTimestamp());
            report.setCollectionDate(DateUtil.getNowAsTimestamp());
            report.setTypeId(QUEUE_TYPE_ID);
            report.setBookkeepingData(getResultIdListString() == null ? "" : getResultIdListString());
            report.setSend(true);

            try {
                SpringContext.getBean(ReportExternalExportService.class).insert(report);
            } catch (LIMSRuntimeException e) {
                LogEvent.logErrorStack(e);
            }
        }

        private String getResultIdListString() {
            String comma = "";

            StringBuilder builder = new StringBuilder();

            for (Result result : reportingResults) {
                builder.append(comma); // empty first time through
                builder.append(result.getId());

                comma = ",";
            }

            return builder.toString();
        }

        private void markFinalResultsAsSent() {
            Timestamp now = DateUtil.getNowAsTimestamp();

            List<DocumentTrack> documents = new ArrayList<>();

            for (Result result : reportingResults) {
                if (result.getResultEvent() == Event.FINAL_RESULT || result.getResultEvent() == Event.CORRECTION) {
                    DocumentTrack document = new DocumentTrack();
                    document.setDocumentTypeId(DOCUMENT_TYPE.getId());
                    document.setRecordId(result.getId());
                    document.setReportTime(now);
                    document.setTableId(RESULT_REFERRANCE_TABLE_ID);
                    document.setSysUserId("1");
                    documents.add(document);
                }
            }

            DocumentTrackService trackService = SpringContext.getBean(DocumentTrackService.class);

            try {
                trackService.insertAll(documents);
//				for (DocumentTrack document : documents) {
//					trackService.insert(document);
//				}
            } catch (LIMSRuntimeException e) {
                LogEvent.logErrorStack(e);
            }
        }
    }
}
