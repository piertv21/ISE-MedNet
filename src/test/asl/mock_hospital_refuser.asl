!start.
+!start <- .df_register("hospital").

+cfp(CnpId, Task)[source(Initiator)]
   <- .abolish(cfp(CnpId, Task));
      .my_name(Me);
      report(cnp_refused(Me, CnpId));
      .send(Initiator, tell, refuse(CnpId, no_capacity)).
