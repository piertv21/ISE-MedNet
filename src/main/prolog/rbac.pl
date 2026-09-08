role_name(equipment_manager).
role_name(control_center).
role_name(triage_nurse).
role_name(ambulance).
role_name(hospital).
role_name(patient).
role_name(doctor).

role_of(Agent, Role) :- role_name(Role), name_matches(Agent, Role), !.

name_matches(Agent, Role) :-
    atom_chars(Agent, AgentChars),
    atom_chars(Role, RoleChars),
    prefix_of(RoleChars, AgentChars).

name_matches(Agent, patient) :-
    atom_chars(Agent, [p, a, t, i, e, n, t | Digits]),
    Digits \== [],
    all_digits(Digits).

prefix_of([], []).
prefix_of([], ['_' | _]).
prefix_of([C | Role], [C | Agent]) :- prefix_of(Role, Agent).

all_digits([]).
all_digits([D | Ds]) :- digit(D), all_digits(Ds).

digit('0'). digit('1'). digit('2'). digit('3'). digit('4').
digit('5'). digit('6'). digit('7'). digit('8'). digit('9').

permitted(ambulance, move_to).
permitted(ambulance, preliminary_triage).
permitted(ambulance, load_patient).
permitted(ambulance, unload_patient).

permitted(hospital, reserve_bed).
permitted(hospital, release_bed).

permitted(triage_nurse, secondary_triage).
permitted(triage_nurse, enqueue_patient).
permitted(triage_nurse, dequeue_patient).
permitted(triage_nurse, requeue_front).

permitted(doctor, lock_equipment).
permitted(doctor, unlock_equipment).
permitted(doctor, run_exam).
permitted(doctor, start_treatment).
permitted(doctor, abort_treatment).
permitted(doctor, discharge_patient).

permitted(equipment_manager, force_release).

can(Agent, Action) :- role_of(Agent, Role), permitted(Role, Action).