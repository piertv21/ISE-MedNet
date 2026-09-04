bid_of(hospital_h1, 130).
bid_of(hospital_h2, 250).
bid_of(hospital_h3, 400).

!start.
+!start <- .df_register("hospital").

+cfp(CnpId, Task)[source(Initiator)]
   <- .abolish(cfp(CnpId, Task));
      .my_name(Me);
      report(cfp_received(Me, CnpId));
      ?bid_of(Me, Bid);
      .send(Initiator, tell, propose(CnpId, Bid)).

+accept_proposal(CnpId, Task)[source(Initiator)]
   <- .abolish(accept_proposal(CnpId, Task));
      .my_name(Me);
      report(cnp_won(Me, CnpId));
      .send(Initiator, tell, admission_confirmed(CnpId, CnpId)).

+reject_proposal(CnpId)[source(_)]
   <- .abolish(reject_proposal(CnpId));
      .my_name(Me);
      report(cnp_lost(Me, CnpId)).

+cancel_admission(CallId, Patient)[source(_)]
   <- .abolish(cancel_admission(CallId, Patient));
      .my_name(Me);
      report(cancelled(Me, CallId)).
