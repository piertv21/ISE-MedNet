% Shared medical and organizational knowledge base. The Java environment queries it for
% durations, default severity codes and the treatment protocol; the agents internalize it
% at start-up and use these clauses as plan context conditions.

% A lower number means more urgent.
code_priority(red, 0).
code_priority(yellow, 1).
code_priority(green, 2).
code_priority(white, 3).

% Admission bid weights by severity: travel cost and specialization mismatch.
% Each code in code_priority/2 must have one corresponding clause.
admission_weights(red,    30, 2000).
admission_weights(yellow, 20,  800).
admission_weights(green,  10,  300).
admission_weights(white,  10,  300).

% The medical specialization each pathology requires.
requires_specialization(cardiac_arrest, cardiology).
requires_specialization(stroke, neurology).
requires_specialization(major_trauma, trauma_surgery).
requires_specialization(fracture, general).
requires_specialization(abdominal_pain, general).

% The severity code assumed for a pathology before any triage is performed.
default_code(cardiac_arrest, red).
default_code(stroke, red).
default_code(major_trauma, yellow).
default_code(fracture, green).
default_code(abdominal_pain, green).

pathology(P) :- requires_specialization(P, _).

required_exams(cardiac_arrest, [ecg, blood_lab]).
required_exams(stroke, [ct_scan, blood_lab]).
required_exams(major_trauma, [ct_scan, xray]).
required_exams(fracture, [xray]).
required_exams(abdominal_pain, [blood_lab]).

% The mutex-protected machine each exam runs on.
equipment_for(ecg, ecg_station).
equipment_for(blood_lab, lab).
equipment_for(ct_scan, ct_scanner).
equipment_for(xray, xray_room).

% How long a job occupies its resource, in simulation ticks.
exam_duration(ecg, 4).
exam_duration(blood_lab, 6).
exam_duration(ct_scan, 8).
exam_duration(xray, 5).

treatment_duration(red, 20).
treatment_duration(yellow, 40).
treatment_duration(green, 25).
treatment_duration(white, 15).

% A red-code treatment may only be performed in the operating room. Enforced twice: by
% the planner (needs_for_treatment) and by the environment (ActionExecutor).
treatment_equipment(red, operating_room).

protocol_max_parallel_patients(doctor, 1).

% Red is never preempted; every less urgent code may be.
protocol_preemptable(Code) :- code_priority(Code, P), P >= 1.