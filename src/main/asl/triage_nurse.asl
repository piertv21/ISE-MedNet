{ include("include/kb_medical.asl") }
{ include("include/cnp_initiator.asl") }

cnp_deadline(1500).

+my_hospital(H)
   :  not ready
   <- .concat("triage_nurse_", H, Service);
      mednet.df.df_register(Service);
      +ready;
      .print("[", H, "] triage nurse on duty").

+handover(CallId, Patient, Pathology, PrelimCode)[source(Amb)]
   :  ready
   <- .abolish(handover(CallId, Patient, Pathology, PrelimCode));
      .print("receiving ", Patient, " (preliminary code ", PrelimCode, ")");
      secondary_triage(Patient);
      .wait(triage_result(Patient, _));
      ?triage_result(Patient, Code);
      .print("secondary triage of ", Patient, ": code ", Code);
      enqueue_patient(Patient, Code);
      +queued(Patient, Pathology, Code);
      !!process_queue.

next_case(P, Path, C) :-
   .findall(cand(Prio, Since, P2, Path2, C2),
            (queued(P2, Path2, C2) & code_priority(C2, Prio) & waiting(P2, C2, Since)),
            Candidates)
   & not .empty(Candidates)
   & .min(Candidates, cand(_, _, P, Path, C)).

@pq[atomic]
+!process_queue
   :  not assigning(_) & next_case(Patient, Pathology, Code)
   <- +assigning(Patient);
      !!run_assignment(Patient, Pathology, Code).
+!process_queue <- true.

+!run_assignment(Patient, Pathology, Code)
   :  my_hospital(H)
   <- .concat("doctor_", H, DoctorsService);
      !cnp_start(t(Patient), treat(Patient, Pathology, Code), DoctorsService, DoctorsService).

+!cnp_awarded(t(Patient), Doctor, treat(Patient, _, Code))
   <- dequeue_patient(Patient);
      -queued(Patient, _, _);
      +under_treatment(Patient, Doctor, Code);
      -assigning(Patient);
      !!process_queue.

preemption_victim(D, P, C) :-
   .findall(victim(Prio, D2, P2, C2),
            (under_treatment(P2, D2, C2) & protocol_preemptable(C2) & code_priority(C2, Prio)),
            Victims)
   & not .empty(Victims)
   & .max(Victims, victim(_, D, P, C)).

+!cnp_no_winner(t(Patient), treat(Patient, Pathology, red))
   :  preemption_victim(Doctor, Victim, VictimCode)
   <- .print("[PREEMPT] red ", Patient, " preempts ", Victim, " (", VictimCode,
             ") on ", Doctor);
      .send(Doctor, tell, preempt_order(Patient, Pathology));
      dequeue_patient(Patient);
      -queued(Patient, _, _);
      +under_treatment(Patient, Doctor, red);
      -assigning(Patient);
      !!process_queue.

+!cnp_no_winner(t(Patient), treat(_, _, _))
   <- -assigning(Patient);
      .wait(2000);
      !!process_queue.

+requeue(Patient, Pathology, Code)[source(Doctor)]
   <- .abolish(requeue(Patient, Pathology, Code));
      -under_treatment(Patient, Doctor, _);
      requeue_front(Patient, Code);
      +queued(Patient, Pathology, Code);
      !!process_queue.

+treatment_completed(Patient)[source(Doctor)]
   <- .abolish(treatment_completed(Patient));
      -under_treatment(Patient, Doctor, _);
      .print(Patient, " treated and discharged by ", Doctor);
      !!process_queue.
