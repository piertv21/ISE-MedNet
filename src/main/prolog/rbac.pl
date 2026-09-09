role_name(equipment_manager).
role_name(control_center).
role_name(triage_nurse).
role_name(ambulance).
role_name(hospital).
role_name(patient).
role_name(doctor).

role_of(Agent, Role) :- role_name(Role), name_matches(Agent, Role), !.

name_matches(Agent, Agent).

name_matches(Agent, Role) :-
    atom_concat(Role, '_', Prefix),
    atom_concat(Prefix, _, Agent).

name_matches(Agent, patient) :-
    atom_concat(patient, Suffix, Agent),
    Suffix \== '',
    atom_chars(Suffix, Digits),
    all_digits(Digits).

all_digits([]).
all_digits([D | Ds]) :- char_code(D, C), C >= 48, C =< 57, all_digits(Ds).

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