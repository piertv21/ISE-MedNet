// Triage Nurse: secondary triage, priority queue and initiator of the in-hospital
// ContractNet toward its own doctors. Serializes the local negotiations, one per
// hospital at a time, and decides red-code preemption when no doctor is free.

{ include("include/kb_bootstrap.asl") }
{ include("include/cnp_initiator.asl") }

cnp_deadline(1500).

+my_hospital(H)
   :  not ready
   <- !load_medical_kb;
      .concat("triage_nurse_", H, Service);
      .df_register(Service);
      +ready;
      .print("[", H, "] triage nurse on duty").

+handover(CallId, Patient, Pathology, PrelimCode)[source(Amb)]
   <- .abolish(handover(CallId, Patient, Pathology, PrelimCode));
      !admit_handover(Patient, Pathology, PrelimCode).

+!admit_handover(Patient, Pathology, PrelimCode)
   :  not ready
   <- .print("handover of ", Patient, " arrived before duty started; waiting");
      .wait(ready);
      !admit_handover(Patient, Pathology, PrelimCode).

+!admit_handover(Patient, Pathology, PrelimCode)
   <- .print("receiving ", Patient, " (preliminary code ", PrelimCode, ")");
      secondary_triage(Patient);
      .wait(triage_result(Patient, _));
      ?triage_result(Patient, Code);
      .print("secondary triage of ", Patient, ": code ", Code);
      enqueue_patient(Patient, Code);
      +queued(Patient, Pathology, Code);
      !!process_queue.

// Highest-priority waiting patient: min over code priority then arrival tick. The tick
// comes from the waiting/3 percept, that is from the environment queue, not from beliefs.
next_case(P, Path, C) :-
   .findall(cand(Prio, Since, P2, Path2, C2),
            (queued(P2, Path2, C2) & code_priority(C2, Prio) & waiting(P2, C2, Since)),
            Candidates)
   & not .empty(Candidates)
   & .min(Candidates, cand(_, _, P, Path, C)).

// assigning/1 serializes the local CNP: one negotiation at a time per hospital, so two
// doctors are never awarded from two rounds started off the same queue state.
@pq[atomic]
+!process_queue
   :  not assigning(_) & next_case(Patient, Pathology, Code)
   <- +assigning(Patient);
      !!run_assignment(Patient, Pathology, Code).
+!process_queue <- true.   // empty queue, or a negotiation is already running

+!run_assignment(Patient, Pathology, Code)
   :  my_hospital(H)
   <- .concat("doctor_", H, DoctorsService);
      // every doctor of this hospital bids; the specialization preference is in the bid
      !cnp_start(t(Patient), treat(Patient, Pathology, Code), DoctorsService, DoctorsService).

+!cnp_awarded(t(Patient), Doctor, treat(Patient, _, Code))
   <- dequeue_patient(Patient);
      -queued(Patient, _, _);
      +under_treatment(Patient, Doctor, Code);
      -assigning(Patient);
      !!process_queue.

// The least critical patient in treatment whose code the protocol allows to preempt.
// protocol_preemptable/1 is derived by the knowledge base and red is never a victim.
preemption_victim(D, P, C) :-
   .findall(victim(Prio, D2, P2, C2),
            (under_treatment(P2, D2, C2) & protocol_preemptable(C2) & code_priority(C2, Prio)),
            Victims)
   & not .empty(Victims)
   & .max(Victims, victim(_, D, P, C)).

// No doctor free and the waiting patient is red, so preempt. This runs from the
// no-winner callback, only after the ordinary negotiation has failed.
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

// Non-red, or no preemptable victim: back off and retry later.
+!cnp_no_winner(t(Patient), treat(_, _, _))
   <- -assigning(Patient);
      .wait(2000);
      !!process_queue.

// A preempted patient comes back: requeue_front puts them at the head of their priority
// class, so being preempted does not cost them their place in line.
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

-!cnp_awarded(t(Patient), _, _)
   <- .print("assignment of ", Patient, " failed; releasing the local CNP");
      .abolish(assigning(Patient));
      !!process_queue.

-!cnp_no_winner(t(Patient), _)
   <- .print("the no-winner handling of ", Patient, " failed; releasing the local CNP");
      .abolish(assigning(Patient));
      !!process_queue.
