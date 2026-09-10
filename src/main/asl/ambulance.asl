// Ambulance agent: preliminary on-site triage and transport to the awarded hospital.
// A new transport order for the same call replaces the current route.

status(free).

!setup.
+!setup
   <- .df_register("ambulance");
      .print("ambulance in service").

+!pickup(CallId, Patient, pos(X, Y))[source(control_center)]
   :  status(free) & not carrying(_)
   <- -+status(busy(CallId));
      move_to(X, Y);
      .wait(at_target(X, Y));
      preliminary_triage(Patient);
      .wait(triage_data(Patient, _, _));
      load_patient(Patient);
      ?triage_data(Patient, Pathology, Code);
      .print("picked up ", Patient, ": ", Pathology, " (prelim code ", Code, ")");
      .send(control_center, tell, triage_report(CallId, Pathology, Code, pos(X, Y))).

// Refusing a second mission must not reset the status: this vehicle is still busy with
// the call it already accepted.
-!pickup(CallId, _, _)
   :  status(busy(Other)) & Other \== CallId
   <- .print("cannot take ", CallId, ": still on ", Other);
      .send(control_center, tell, pickup_failed(CallId)).

-!pickup(CallId, _, _)
   <- .print("pickup failed for ", CallId);
      -+status(free);
      .send(control_center, tell, pickup_failed(CallId)).

+transport_to(CallId, Hospital)[source(control_center)]
   :  status(busy(CallId)) & not transporting(CallId, _)
   <- .abolish(transport_to(CallId, Hospital));
      +transporting(CallId, Hospital);
      !!do_transport(CallId, Hospital).

// A different destination for the same call drops the running transport intention and
// replaces the route, patient still on board.
+transport_to(CallId, NewHospital)[source(control_center)]
   :  transporting(CallId, OldHospital) & OldHospital \== NewHospital
   <- .abolish(transport_to(CallId, NewHospital));
      .drop_intention(do_transport(CallId, OldHospital));
      -+transporting(CallId, NewHospital);
      .print("[REROUTE] ", CallId, ": ", OldHospital, " -> ", NewHospital);
      !!do_transport(CallId, NewHospital).

+!do_transport(CallId, Hospital)
   :  hospital_info(Hospital, Nurse, HX, HY)
   <- move_to(HX, HY);
      .wait(at_target(HX, HY));
      !handover(CallId, Hospital, Nurse).

+!handover(CallId, Hospital, Nurse)
   <- ?carrying(Patient);
      ?triage_data(Patient, Pathology, Code);
      unload_patient(Patient);
      .print("handover of ", Patient, " to ", Nurse);
      .send(Nurse, tell, handover(CallId, Patient, Pathology, Code));
      .abolish(transporting(CallId, _));
      -+status(free);
      // The handover closes the network negotiation: the control center stops reopening
      // it and undoes any award still in flight.
      .send(control_center, tell, delivered(CallId, Hospital));
      .send(control_center, tell, ambulance_free).

// The hospital could not take the patient, its bed having gone to someone else during
// the drive. The patient is still on board, so stay on mission and wait for a new
// destination.
-!do_transport(CallId, Hospital)
   :  carrying(_)
   <- .print("delivery of ", CallId, " to ", Hospital, " failed; awaiting a new destination");
      .abolish(transporting(CallId, _));
      .send(control_center, tell, transport_failed(CallId, Hospital)).

// Nothing on board: the mission is over.
-!do_transport(CallId, Hospital)
   <- .print("transport of ", CallId, " toward ", Hospital, " failed");
      .abolish(transporting(CallId, _));
      -+status(free);
      .send(control_center, tell, transport_failed(CallId, Hospital));
      .send(control_center, tell, ambulance_free).
