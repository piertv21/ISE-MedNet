{ include("include/kb_medical.asl") }
{ include("include/cnp_participant.asl") }

+my_hospital(H)
   :  my_specialization(Spec) & not ready
   <- .concat("doctor_", H, GenericService);
      mednet.df.df_register(GenericService);
      .concat("doctor_", H, "_", Spec, SpecializedService);
      mednet.df.df_register(SpecializedService);
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
   :  required_exams(Pathology, Exams) & .length(Exams, N)
   <- +exams_pending(P, N);
      for (.member(Exam, Exams)) {
         !!do_exam(P, Exam);
      };
      .wait(all_exams_done(P));
      !finalize(P).

+!do_exam(P, Exam)
   :  equipment_for(Exam, Equipment)
   <- +my_exam(P, Exam);
      !acquire(Equipment, P, Exam);
      lock_equipment(Equipment);
      +holding(Equipment, P, Exam);
      run_exam(P, Exam, Equipment);
      .wait(exam_done(P, Exam));
      !release(Equipment, P, Exam);
      -my_exam(P, Exam);
      !exam_barrier(P).

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
+!exam_barrier(P)
   :  exams_pending(P, N) & N > 1
   <- -exams_pending(P, N);
      +exams_pending(P, N - 1).
@barrier2[atomic]
+!exam_barrier(P)
   :  exams_pending(P, 1)
   <- -exams_pending(P, 1);
      +all_exams_done(P).
+!exam_barrier(_) <- true.   

-!do_exam(P, Exam)
   :  holding(Equipment, P, Exam)
   <- .print("exam ", Exam, " for ", P, " aborted; releasing ", Equipment);
      !release(Equipment, P, Exam);
      -my_exam(P, Exam);
      !exam_barrier(P).
-!do_exam(P, Exam)
   <- .abolish(my_exam(P, Exam));
      .abolish(req_id(P, Exam, _));
      !exam_barrier(P).

+!finalize(P)
   :  treating(P, Code) & treatment_equipment(Code, Equipment)
   <- !acquire(Equipment, P, treatment);
      lock_equipment(Equipment);
      +holding(Equipment, P, treatment);
      start_treatment(P);
      .wait(treatment_done(P));
      !release(Equipment, P, treatment);
      !close_case(P).

+!finalize(P)
   :  treating(P, _)
   <- start_treatment(P);
      .wait(treatment_done(P));
      !close_case(P).

+!finalize(_) <- true.   

-!finalize(P)
   :  treating(P, _)
   <- .print("finalization of ", P, " delayed; retrying");
      .wait(2000);
      !finalize(P).

+!close_case(P)
   :  my_hospital(H)
   <- discharge_patient(P);
      -treating(P, _);
      .abolish(all_exams_done(P));
      .abolish(exams_pending(P, _));
      .print(P, " discharged");
      .concat("triage_nurse_", H, NurseService);
      mednet.df.df_search(NurseService, [Nurse | _]);
      .send(Nurse, tell, treatment_completed(P)).

+preempt_order(NewP, NewPathology)[source(Nurse)]
   :  treating(OldP, OldCode) & protocol_preemptable(OldCode)
   <- .abolish(preempt_order(NewP, NewPathology));
      .print("[PREEMPT] dropping ", OldP, " (", OldCode, ") for red ", NewP);
      .drop_intention(treat_patient(OldP, _));
      !cancel_exams(OldP);
      abort_treatment(OldP);
      -treating(OldP, OldCode);
      .abolish(exams_pending(OldP, _));
      .abolish(all_exams_done(OldP));
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

+!cancel_exams(P)
   :  my_exam(P, Exam)
   <- .fail_goal(do_exam(P, Exam));
      .wait(50);
      !cancel_exams(P).
+!cancel_exams(_) <- true.
-!cancel_exams(_) <- true.
