// Two scripted handovers: the yellow case first, the red one 4 s later, late enough for
// the yellow to be under treatment when the red arrives.

!start.
+!start
   <- .wait(1000);
      .send(triage_nurse_h1, tell, handover(call_1, patient1, fracture, yellow));
      .wait(4000);
      .send(triage_nurse_h1, tell, handover(call_2, patient2, cardiac_arrest, red)).
