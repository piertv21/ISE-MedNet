!start.
+!start
   <- .wait(1000);
      .send(triage_nurse_h1, tell, handover(call_1, patient1, fracture, yellow));
      .wait(4000);
      .send(triage_nurse_h1, tell, handover(call_2, patient2, cardiac_arrest, red)).
