{ include("include/kb_bootstrap.asl") }
{ include("include/cnp_participant.asl") }

+my_hospital(H)
   :  my_specialization(Spec) & not ready
   <- !load_medical_kb;
      .concat("doctor_", H, GenericService);
      .df_register(GenericService);
      .concat("doctor_", H, "_", Spec, SpecializedService);
      .df_register(SpecializedService);
      +ready;
      .print("on duty at ", H, " (", Spec, ")").

+!make_bid(t(P), treat(P, Pathology, _), Bid)
   :  ready & my_specialization(MySpec)
      & protocol_max_parallel_patients(doctor, Max) & .count(treating(_, _), N) & N < Max
   <- if (requires_specialization(Pathology, MySpec)) {
         Bid = 0;
      } else {
         Bid = 10;
      }.

+!on_award(t(P), treat(P, Pathology, Code))
   <- +treating(P, Code);
      .print("taking charge of ", P, " (", Pathology, ", code ", Code, ")");
      !!treat_patient(P, Pathology).


+!treat_patient(P, Pathology)
   :  treating(P, Code)
   <- .findall(Exam, exam_completed(P, Exam), Done);
      mednet.plan.care_stages(P, Pathology, Code, Done, Stages);
      .print("[PLAN] ", P, " (", Pathology, ", ", Code, ", done: ", Done, "): ", Stages);
      !run_stages(P, Stages).

+!treat_patient(_, _).

-!treat_patient(P, _)
   :  treating(P, _)
   <- .print("could not plan the care of ", P, "; retrying");
      .wait(2000);
      ?patient_pathology(P, Pathology);
      !!treat_patient(P, Pathology).

+!run_stages(_, []).
+!run_stages(P, [Stage | Rest])
   <- .length(Stage, Segments);
      +stage_pending(P, Segments);
      for (.member(Segment, Stage)) {
         !!run_segment(P, Segment);
      };
      .wait(stage_done(P));
      -stage_done(P);
      !run_stages(P, Rest).

+!run_segment(P, Segment)
   <- +my_segment(P, Segment);
      for (.member(Step, Segment)) { !step(P, Step) };
      -my_segment(P, Segment);
      !stage_barrier(P).

+!step(P, acquire(Equipment))
   <- !acquire(Equipment, P, Equipment);
      lock_equipment(Equipment);
      +holding(Equipment, P, Equipment).

+!step(P, exam(Exam, Equipment))
   <- run_exam(P, Exam, Equipment);
      .wait(exam_done(P, Exam)).

+!step(P, release(Equipment))
   <- !release(Equipment, P, Equipment).

+!step(P, treat)
   <- start_treatment(P);
      .wait(treatment_done(P)).

+!step(P, discharge)
   <- !close_case(P).

-!step(P, treat)
   :  treating(P, _)
   <- .print("treatment of ", P, " delayed; retrying");
      .wait(2000);
      !step(P, treat).

+!acquire(Equipment, P, Use)
   :  treating(P, Code) & code_priority(Code, Priority) & my_hospital(H)
   <- .concat("equipment_manager_", H, Manager);
      .concat(P, "_", Use, ReqId);
      +req_id(P, Use, ReqId);
      .send(Manager, tell, request_equipment(ReqId, Equipment, Priority));
      .wait(granted(ReqId, Equipment), 20000);
      .abolish(granted(ReqId, Equipment)).

+!release(Equipment, P, Use)
   :  my_hospital(H) & req_id(P, Use, ReqId)
   <- unlock_equipment(Equipment);
      -holding(Equipment, P, Use);
      -req_id(P, Use, ReqId);
      .concat("equipment_manager_", H, Manager);
      .send(Manager, tell, released(ReqId, Equipment)).

@barrier1[atomic]
+!stage_barrier(P)
   :  stage_pending(P, N) & N > 1
   <- -stage_pending(P, N);
      +stage_pending(P, N - 1).
@barrier2[atomic]
+!stage_barrier(P)
   :  stage_pending(P, 1)
   <- -stage_pending(P, 1);
      +stage_done(P).
+!stage_barrier(_) <- true.

-!run_segment(P, Segment)
   :  .member(acquire(Equipment), Segment) & holding(Equipment, P, Equipment)
   <- .print("segment of ", P, " aborted; releasing ", Equipment);
      !release(Equipment, P, Equipment);
      -my_segment(P, Segment);
      !stage_barrier(P).
-!run_segment(P, Segment)
   :  .member(acquire(Equipment), Segment)
   <- .abolish(my_segment(P, Segment));
      .abolish(req_id(P, Equipment, _));
      !stage_barrier(P).
-!run_segment(P, Segment)
   <- .abolish(my_segment(P, Segment));
      !stage_barrier(P).

+!close_case(P)
   :  my_hospital(H)
   <- discharge_patient(P);
      -treating(P, _);
      .abolish(stage_done(P));
      .abolish(stage_pending(P, _));
      .print(P, " discharged");
      .concat("triage_nurse_", H, NurseService);
      .df_search(NurseService, [Nurse | _]);
      .send(Nurse, tell, treatment_completed(P)).

+preempt_order(NewP, NewPathology)[source(Nurse)]
   :  treating(OldP, OldCode) & protocol_preemptable(OldCode)
   <- .abolish(preempt_order(NewP, NewPathology));
      .print("[PREEMPT] dropping ", OldP, " (", OldCode, ") for red ", NewP);
      .drop_intention(treat_patient(OldP, _));
      !cancel_segments(OldP);
      abort_treatment(OldP);
      -treating(OldP, OldCode);
      .abolish(stage_pending(OldP, _));
      .abolish(stage_done(OldP));
      ?patient_pathology(OldP, OldPathology);
      .send(Nurse, tell, requeue(OldP, OldPathology, OldCode));
      +treating(NewP, red);
      !!treat_patient(NewP, NewPathology).

+preempt_order(NewP, NewPathology)[source(Nurse)]
   :  not treating(_, _)
   <- .abolish(preempt_order(NewP, NewPathology));
      +treating(NewP, red);
      !!treat_patient(NewP, NewPathology).

+preempt_order(NewP, NewPathology)[source(Nurse)]
   <- .abolish(preempt_order(NewP, NewPathology));
      .send(Nurse, tell, requeue(NewP, NewPathology, red)).

+!cancel_segments(P)
   :  my_segment(P, Segment)
   <- .fail_goal(run_segment(P, Segment));
      .wait(50);
      !cancel_segments(P).
+!cancel_segments(_) <- true.
-!cancel_segments(_) <- true.
