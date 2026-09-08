{ include("include/kb_bootstrap.asl") }
{ include("include/cnp_initiator.asl") }

call_counter(0).
cnp_deadline(2000).

!load_medical_kb.

@cc_next_id[atomic]
+!next_call_id(Id)
   :  call_counter(N)
   <- -+call_counter(N + 1);
      Id = N + 1.

+!handle_emergency(Patient, Pos, Pathology)[source(Patient)]
   <- !next_call_id(N);
      .concat("call_", N, CallId);
      +emergency(CallId, Patient, Pos, Pathology);
      .print("[CC] ", CallId, ": ", Patient, " reports ", Pathology, " at ", Pos);
      !dispatch_ambulance(CallId).

+!dispatch_ambulance(CallId)
   :  emergency(CallId, Patient, pos(X, Y), _)
      & .findall(d(Dist, A),
                 (ambulance_pos(A, pos(AX, AY)) & not busy_amb(A)
                  & Dist = math.abs(AX - X) + math.abs(AY - Y)),
                 Candidates)
      & not .empty(Candidates)
   <- .min(Candidates, d(_, Amb));
      +busy_amb(Amb);
      +dispatched(CallId, Amb);
      .print("[CC] dispatching ", Amb, " for ", CallId);
      .send(Amb, achieve, pickup(CallId, Patient, pos(X, Y))).

+!dispatch_ambulance(CallId)
   :  emergency(CallId, Patient, Pos, _)
   <- .df_search("ambulance", Ambulances);
      !pick_free(Ambulances, Amb);
      +busy_amb(Amb);
      +dispatched(CallId, Amb);
      .print("[CC] dispatching ", Amb, " for ", CallId);
      .send(Amb, achieve, pickup(CallId, Patient, Pos)).

-!dispatch_ambulance(CallId)
   <- .wait(1000);
      !dispatch_ambulance(CallId).

+!pick_free([A | _], A) : not busy_amb(A) <- true.
+!pick_free([_ | T], A) <- !pick_free(T, A).

+ambulance_free[source(A)]
   <- .abolish(ambulance_free[source(A)]);
      -busy_amb(A).

+triage_report(CallId, Pathology, Code, Pos)[source(Amb)]
   :  emergency(CallId, Patient, _, _)
   <- .abolish(triage_report(CallId, Pathology, Code, Pos));
      !load_medical_kb;
      ?requires_specialization(Pathology, Spec);
      -+emergency_code(CallId, Code);
      .print("[CC] on-site triage for ", CallId, ": ", Pathology, ", code ", Code);
      .concat("hospital_", Spec, SpecializedService);
      !cnp_start(CallId, admission(CallId, Patient, Pathology, Code, Pos),
                 SpecializedService, "hospital").

+!cnp_awarded(CallId, Hospital, admission(CallId, Patient, _, _, _))
   :  settled(CallId)
   <- .print("[CC] ", CallId, " already delivered; cancelling the admission at ", Hospital);
      .send(Hospital, tell, cancel_admission(CallId, Patient)).

+!cnp_awarded(CallId, Hospital, admission(CallId, Patient, _, _, _))
   :  dispatched(CallId, Amb) & admitted_to(CallId, Previous) & Previous \== Hospital
   <- .print("[CC] ", CallId, " moved from ", Previous, " to ", Hospital);
      .send(Previous, tell, cancel_admission(CallId, Patient));
      -+admitted_to(CallId, Hospital);
      .send(Amb, tell, transport_to(CallId, Hospital)).

+!cnp_awarded(CallId, Hospital, admission(CallId, Patient, _, _, _))
   :  dispatched(CallId, Amb)
   <- -+admitted_to(CallId, Hospital);
      .send(Amb, tell, transport_to(CallId, Hospital)).

+!cnp_no_winner(CallId, admission(CallId, _, _, _, _))
   :  settled(CallId)
   <- true.

+!cnp_no_winner(CallId, admission(CallId, Patient, Pathology, Code, Pos))
   <- .print("[CNP-NET] no hospital can admit ", CallId, "; retrying");
      .abolish(excluded(CallId, _));
      .wait(1500);
      !cnp_start(CallId, admission(CallId, Patient, Pathology, Code, Pos),
                 "hospital", "hospital").

+delivered(CallId, Hospital)[source(Amb)]
   <- .abolish(delivered(CallId, Hospital));
      +settled(CallId);
      -+admitted_to(CallId, Hospital);
      .print("[CC] ", CallId, " delivered to ", Hospital, "; negotiation closed");
      .abolish(excluded(CallId, _));
      .abolish(emergency(CallId, _, _, _));
      .abolish(emergency_code(CallId, _));
      .abolish(dispatched(CallId, _)).

+admission_confirmed(CnpId, CallId)[source(H)]
   <- .abolish(admission_confirmed(CnpId, CallId));
      .print("[CC] ", H, " is holding the bed for ", CallId).

+divert_request(CallId)[source(_)]
   :  settled(CallId)
   <- .abolish(divert_request(CallId)).

+divert_request(CallId)[source(H)]
   :  emergency(CallId, Patient, _, Pathology) & emergency_code(CallId, Code)
      & dispatched(CallId, Amb)
   <- .abolish(divert_request(CallId));
      +excluded(CallId, H);
      .print("[RENEG] ", H, " lost capacity for ", CallId, "; reopening network CNP");
      !renegotiate(CallId, Patient, Pathology, Code, Amb).

+!renegotiate(CallId, Patient, Pathology, Code, Amb)
   :  ambulance_pos(Amb, Pos)
   <- !cnp_start(CallId, admission(CallId, Patient, Pathology, Code, Pos),
                 "hospital", "hospital").

+!renegotiate(CallId, Patient, Pathology, Code, _)
   :  emergency(CallId, _, Pos, _)
   <- !cnp_start(CallId, admission(CallId, Patient, Pathology, Code, Pos),
                 "hospital", "hospital").

+pickup_failed(CallId)[source(A)]
   <- .abolish(pickup_failed(CallId));
      -busy_amb(A);
      -dispatched(CallId, A);
      .print("[CC] pickup failed for ", CallId, "; re-dispatching");
      .wait(500);
      !dispatch_ambulance(CallId).

+transport_failed(CallId, H)
   :  settled(CallId)
   <- .abolish(transport_failed(CallId, H)).

+transport_failed(CallId, H)[source(A)]
   :  admitted_to(CallId, H) & emergency(CallId, Patient, _, Pathology)
      & emergency_code(CallId, Code)
   <- .abolish(transport_failed(CallId, H));
      +excluded(CallId, H);
      .print("[CC] ", A, " could not deliver ", CallId, " to ", H, "; reopening network CNP");
      !renegotiate(CallId, Patient, Pathology, Code, A).

+transport_failed(CallId, H)
   <- .abolish(transport_failed(CallId, H)).
