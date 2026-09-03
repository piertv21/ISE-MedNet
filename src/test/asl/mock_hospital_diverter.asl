!start.
+!start <- mednet.df.df_register("hospital").

+cfp(CnpId, Task)[source(Initiator)]
   <- .abolish(cfp(CnpId, Task));
      .send(Initiator, tell, propose(CnpId, 100)).

+accept_proposal(CnpId, Task)[source(Initiator)]
   <- .abolish(accept_proposal(CnpId, Task));
      .my_name(Me);
      report(cnp_won(Me, CnpId));
      .send(Initiator, tell, admission_confirmed(CnpId, CnpId));
      .wait(500);
      report(divert_sent(Me, CnpId));
      .send(Initiator, tell, divert_request(CnpId)).
