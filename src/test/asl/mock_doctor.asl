// Fixed bids: the specialized doctor is always the cheaper one, so the local CNP has a
// deterministic winner.

bid_of(doctor_h1_neurology, 0).
bid_of(doctor_h1_general, 10).

!start.
+!start <- .df_register("doctor_h1").

+cfp(CnpId, Task)[source(Initiator)]
   <- .abolish(cfp(CnpId, Task));
      .my_name(Me);
      ?bid_of(Me, Bid);
      .send(Initiator, tell, propose(CnpId, Bid)).

+accept_proposal(t(P), Task)[source(Initiator)]
   <- .abolish(accept_proposal(t(P), Task));
      .my_name(Me);
      report(treatment_assigned(Me, P));
      .wait(500);
      .send(Initiator, tell, treatment_completed(P)).

+reject_proposal(CnpId)[source(_)]
   <- .abolish(reject_proposal(CnpId));
      .my_name(Me);
      report(treatment_rejected(Me)).
