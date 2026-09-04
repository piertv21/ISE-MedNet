!start.
+!start <- .df_register("doctor_h1").

+cfp(CnpId, Task)[source(Initiator)]
   :  not busy
   <- .abolish(cfp(CnpId, Task));
      .send(Initiator, tell, propose(CnpId, 5)).

+cfp(CnpId, Task)[source(Initiator)]
   <- .abolish(cfp(CnpId, Task));
      .send(Initiator, tell, refuse(CnpId, busy)).

+accept_proposal(t(P), Task)[source(Initiator)]
   <- .abolish(accept_proposal(t(P), Task));
      +busy;
      +current_patient(P);
      .my_name(Me);
      report(treatment_assigned(Me, P)).

+preempt_order(NewP, NewPathology)[source(Nurse)]
   :  current_patient(OldP)
   <- .abolish(preempt_order(NewP, NewPathology));
      .my_name(Me);
      report(preempt_received(Me, NewP, OldP));
      -current_patient(OldP);
      +current_patient(NewP);
      .send(Nurse, tell, requeue(OldP, fracture, yellow)).
