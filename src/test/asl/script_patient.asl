// Scripted stimulus: the call is simply sent, with no percepts. The initial wait lets the
// other agents register in the DF, or the first CNP round finds nobody.

!start.
+!start
   <- .wait(1000);
      .my_name(Me);
      .send(control_center, achieve, handle_emergency(Me, pos(3, 3), stroke)).
