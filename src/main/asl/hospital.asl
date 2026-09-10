// Hospital agent: network-CNP participant and capacity monitor. Registers in the
// Directory Facilitator as a generic "hospital" and once per owned specialization.

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

// cost = distance*W(Code) + occupancy% + specialization penalty P(Code)
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
// No fallback plan: with beds_free(0) the context above fails and the library refuses.

// The patient is here, or a bed is already held for them, and a stale round has
// re-awarded us.
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

// The last free bed vanished between bid and award: ask for a renegotiation.
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

// The bed may already be gone, stolen by a walk-in or never reserved because the award
// was stale. Either way there is nothing left to free, and that is not an error.
+!free_reservation(Patient) <- release_bed(Patient).
-!free_reservation(_) <- true.

// Safety lease: a bed held for a patient who never arrives is eventually given back.
+reservation_expired(Patient)
   :  my_hospital(H)
   <- .print("[", H, "] reservation for ", Patient, " expired; releasing the bed");
      .abolish(inbound_case(_, Patient, _));
      !free_reservation(Patient).

// A walk-in consumed the bed reserved for a patient still inbound, which triggers the
// mid-transport renegotiation.
+reservation_lost(Patient)
   :  inbound_case(CallId, Patient, Code) & my_hospital(H)
   <- .print("[", H, "] bed for inbound ", Patient, " lost to a walk-in emergency; diverting");
      -inbound_case(CallId, Patient, Code);
      .send(control_center, tell, divert_request(CallId)).