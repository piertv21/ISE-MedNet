{ include("include/kb_bootstrap.asl") }
{ include("include/cnp_participant.asl") }

+my_hospital(H)
   :  not ready
   <- !load_medical_kb;
      .df_register("hospital");
      for (my_specialization(S)) {
         .concat("hospital_", S, Service);
         .df_register(Service);
      };
      +ready;
      .print("[", H, "] registered in the DF").

+!make_bid(CnpId, admission(_CallId, _Patient, Pathology, Code, pos(X, Y)), Bid)
   :  ready & beds_free(B) & B > 0 & my_pos(HX, HY) & beds_capacity(Cap)
      & admission_weights(Code, DistWeight, SpecPenalty)
   <- if (requires_specialization(Pathology, S) & my_specialization(S)) {
         Penalty = 0;
      } else {
         Penalty = SpecPenalty;
      };
      Bid = (math.abs(HX - X) + math.abs(HY - Y)) * DistWeight
            + (Cap - B) * 100 / Cap + Penalty.

+!on_award(CnpId, admission(CallId, Patient, _, _, _))
   :  my_hospital(H) & (arrived(Patient) | inbound_case(_, Patient, _))
   <- .print("[", H, "] ", Patient, " is already ours; award acknowledged");
      .send(control_center, tell, admission_confirmed(CnpId, CallId)).

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

+cancel_admission(CallId, Patient)[source(control_center)]
   :  my_hospital(H)
   <- .abolish(cancel_admission(CallId, Patient));
      .abolish(inbound_case(_, Patient, _));
      .print("[", H, "] admission of ", Patient, " cancelled; releasing the bed");
      !free_reservation(Patient).

+!free_reservation(Patient) <- release_bed(Patient).
-!free_reservation(_) <- true.

+reservation_expired(Patient)
   :  my_hospital(H)
   <- .print("[", H, "] reservation for ", Patient, " expired; releasing the bed");
      .abolish(inbound_case(_, Patient, _));
      !free_reservation(Patient).

+reservation_lost(Patient)
   :  inbound_case(CallId, Patient, Code) & my_hospital(H)
   <- .print("[", H, "] bed for inbound ", Patient, " lost to a walk-in emergency; diverting");
      -inbound_case(CallId, Patient, Code);
      .send(control_center, tell, divert_request(CallId)).