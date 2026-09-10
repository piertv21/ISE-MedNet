// Fails the first delivery only, then succeeds. With the diverter hospital this makes one
// event, the lost bed, reach the control center twice.

!start.
+!start <- .df_register("ambulance").

+!pickup(CallId, Patient, Pos)[source(control_center)]
   <- .my_name(Me);
      report(pickup_ordered(Me, CallId, Patient));
      .send(control_center, tell, triage_report(CallId, stroke, red, Pos)).

+transport_to(CallId, Hospital)[source(control_center)]
   :  not tried(CallId, _)
   <- .abolish(transport_to(CallId, Hospital));
      +tried(CallId, Hospital);
      .my_name(Me);
      report(transport_ordered(Me, CallId, Hospital));
      .send(control_center, tell, transport_failed(CallId, Hospital)).

+transport_to(CallId, Hospital)[source(control_center)]
   <- .abolish(transport_to(CallId, Hospital));
      .my_name(Me);
      report(transport_ordered(Me, CallId, Hospital));
      report(delivered(Me, CallId, Hospital));
      .send(control_center, tell, delivered(CallId, Hospital)).
