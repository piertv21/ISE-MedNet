% The STRIPS domain of a doctor taking charge of one patient. Since the plan is derived
% rather than scripted, a patient interrupted by a red-code preemption is replanned from
% what is already done and the new plan skips the exams completed before the interruption.

action(exam(Exam, Equipment),
       [pending_exam(Exam), equipment_for(Exam, Equipment), held(Equipment)],
       [done(Exam)],
       [pending_exam(Exam)]).

action(release(Equipment),
       [held(Equipment)],
       [available(Equipment)],
       [held(Equipment)]).

action(treat,
       [all_exams_done, treatment_resources],
       [treated],
       []).

% The case is closed with empty hands: everything acquired has been released.
action(discharge,
       [treated, nothing_held],
       [discharged],
       []).

action(acquire(Equipment),
       [available(Equipment), needed(Equipment)],
       [held(Equipment)],
       [available(Equipment)]).

holds_in(Condition, State) :- member(Condition, State).

holds_in(equipment_for(Exam, Equipment), _) :- equipment_for(Exam, Equipment).

holds_in(all_exams_done, State) :- \+ member(pending_exam(_), State).
holds_in(nothing_held, State) :- \+ member(held(_), State).
holds_in(treatment_resources, State) :-
    forall(member(needs_for_treatment(Equipment), State), member(held(Equipment), State)).

% A machine is needed while a still-pending exam runs on it, or while the treatment
holds_in(needed(Equipment), State) :- needed_equipment(Equipment, State), !.

needed_equipment(Equipment, State) :-
    member(pending_exam(Exam), State),
    equipment_for(Exam, Equipment).
needed_equipment(Equipment, State) :-
    member(needs_for_treatment(Equipment), State),
    \+ member(treated, State).

care_plan(Pathology, Code, DoneExams, Plan) :-
    initial_state(Pathology, Code, DoneExams, State),
    plan(State, [discharged], Plan).

% The same plan grouped into stages of independent segments. The segments inside one
% stage use different machines and run as parallel sub-intentions of the doctor.
care_stages(Pathology, Code, DoneExams, Stages) :-
    care_plan(Pathology, Code, DoneExams, Plan),
    plan_stages(Plan, Stages).

initial_state(Pathology, Code, DoneExams, State) :-
    required_exams(Pathology, Exams),
    findall(pending_exam(E), (member(E, Exams), \+ member(E, DoneExams)), Pending),
    findall(done(E), (member(E, Exams), member(E, DoneExams)), Completed),
    findall(available(Eq),
            (member(E, Exams), \+ member(E, DoneExams), equipment_for(E, Eq)),
            Machines),
    treatment_extras(Code, Extras),
    append(Pending, Completed, S1),
    append(S1, Machines, S2),
    append(S2, Extras, State).

% A red code may only be treated in the operating room, so that machine is both needed
% and, from this doctor's point of view, available to take.
treatment_extras(Code, [available(Equipment), needs_for_treatment(Equipment)]) :-
    treatment_equipment(Code, Equipment),
    !.
treatment_extras(_, []).

% A segment is one acquire to release block, or a single standalone action.
plan_segments([], []).
plan_segments([acquire(Eq) | Rest], [[acquire(Eq) | Body] | More]) :-
    !,
    segment_body(Eq, Rest, Body, Tail),
    plan_segments(Tail, More).
plan_segments([Action | Rest], [[Action] | More]) :-
    plan_segments(Rest, More).

segment_body(_, [], [], []).
segment_body(Eq, [release(Eq) | Tail], [release(Eq)], Tail) :- !.
segment_body(Eq, [Action | Rest], [Action | Body], Tail) :-
    segment_body(Eq, Rest, Body, Tail).

plan_stages(Plan, Stages) :-
    plan_segments(Plan, Segments),
    group_stages(Segments, Stages).

% Consecutive exam segments on different machines form one parallel stage. Anything else,
% such as the treatment or the discharge, is a stage of its own.
group_stages([], []).
group_stages([Segment | Rest], [[Segment | Parallel] | More]) :-
    exam_segment(Segment),
    !,
    segment_equipment(Segment, Equipment),
    parallel_run(Rest, [Equipment], Parallel, Tail),
    group_stages(Tail, More).
group_stages([Segment | Rest], [[Segment] | More]) :-
    group_stages(Rest, More).

parallel_run([Segment | Rest], Used, [Segment | Parallel], Tail) :-
    exam_segment(Segment),
    segment_equipment(Segment, Equipment),
    \+ member(Equipment, Used),
    !,
    parallel_run(Rest, [Equipment | Used], Parallel, Tail).
parallel_run(Rest, _, [], Rest).

exam_segment(Segment) :- member(exam(_, _), Segment).

% How many steps a staged plan contains, counting the leaves of Stages. Grouping neither
% drops nor duplicates an action, so this equals the length of the linear plan.
plan_length(Stages, N) :-
    findall(Step,
            (member(Stage, Stages), member(Segment, Stage), member(Step, Segment)),
            Steps),
    length(Steps, N).

segment_equipment(Segment, Equipment) :- member(acquire(Equipment), Segment), !.
