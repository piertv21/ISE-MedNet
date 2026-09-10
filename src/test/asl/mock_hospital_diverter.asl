// Wins every round it joins with a bid of 100, then gives the bed straight back, which
// drives the renegotiation and double-trigger tests.

!start.
+!start <- .df_register("hospital").

+cfp(CnpId, Task)[source(Initiator)]
   <- .abolish(cfp(CnpId, Task));
      .my_name(Me);
      report(cfp_received(Me, CnpId));
      .send(Initiator, tell, propose(CnpId, 100)).

+accept_proposal(CnpId, Task)[source(Initiator)]
   <- .abolish(accept_proposal(CnpId, Task));
      .my_name(Me);
      report(cnp_won(Me, CnpId));
      .send(Initiator, tell, admission_confirmed(CnpId, CnpId));
      .wait(500);
      report(divert_sent(Me, CnpId));
      .send(Initiator, tell, divert_request(CnpId)).

+cancel_admission(CallId, Patient)[source(_)]
   <- .abolish(cancel_admission(CallId, Patient));
      .my_name(Me);
      report(cancelled(Me, CallId)).
