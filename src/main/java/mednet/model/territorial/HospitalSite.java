package mednet.model.territorial;

public record HospitalSite(String id, int x, int y) {

    public String hospitalAgent() {
        return "hospital_" + id;
    }

    public String nurseAgent() {
        return "triage_nurse_" + id;
    }
}
