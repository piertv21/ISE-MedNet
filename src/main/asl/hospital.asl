{ include("include/kb_medical.asl") }
{ include("include/cnp_participant.asl") }

+my_hospital(H)
   :  not ready
   <- mednet.df.df_register("hospital");
      for (my_specialization(S)) {
         .concat("hospital_", S, Service);
         mednet.df.df_register(Service);
      };
      +ready;
      .print("[", H, "] registered in the DF").

+!make_bid(CnpId, admission(_CallId, _Patient, Pathology, _Code, pos(X, Y)), Bid)
   :  ready & beds_free(B) & B > 0 & my_pos(HX, HY) & beds_capacity(Cap)
   <- if (requires_specialization(Pathology, S) & my_specialization(S)) {
         Penalty = 0;
      } else {
         Penalty = 500;
      };
      Bid = (math.abs(HX - X) + math.abs(HY - Y)) * 10 + (Cap - B) * 100 / Cap + Penalty.

+!on_award(CnpId, admission(CallId, Patient, _, Code, _))
   :  my_hospital(H)
   <- reserve_bed(Patient);
      +inbound_case(CallId, Patient, Code);
      .print("[", H, "] admission of ", Patient, " confirmed (", CallId, ", code ", Code, ")");
      .send(control_center, tell, admission_confirmed(CnpId, CallId)).

-!on_award(CnpId, admission(CallId, Patient, _, _, _))
   :  my_hospital(H)
   <- .print("[", H, "] could not reserve a bed for ", Patient, "; requesting divert");
      .send(control_center, tell, divert_request(CallId)).

+arrived(Patient)
   :  inbound_case(CallId, Patient, Code)
   <- -inbound_case(CallId, Patient, Code).

+reservation_lost(Patient)
   :  inbound_case(CallId, Patient, Code) & my_hospital(H)
   <- .print("[", H, "] bed for inbound ", Patient, " lost to a walk-in emergency; diverting");
      -inbound_case(CallId, Patient, Code);
      .send(control_center, tell, divert_request(CallId)).
