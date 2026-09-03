package mednet.model.hospital;

import mednet.model.patient.SeverityCode;

public record TriageEntry(String patient, SeverityCode code, long arrivalSeq, long sinceTick) {
}
