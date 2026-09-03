code_priority(red, 0).
code_priority(yellow, 1).
code_priority(green, 2).
code_priority(white, 3).

requires_specialization(cardiac_arrest, cardiology).
requires_specialization(stroke, neurology).
requires_specialization(major_trauma, trauma_surgery).
requires_specialization(fracture, general).
requires_specialization(abdominal_pain, general).

required_exams(cardiac_arrest, [ecg, blood_lab]).
required_exams(stroke, [ct_scan, blood_lab]).
required_exams(major_trauma, [ct_scan, xray]).
required_exams(fracture, [xray]).
required_exams(abdominal_pain, [blood_lab]).

equipment_for(ecg, ecg_station).
equipment_for(blood_lab, lab).
equipment_for(ct_scan, ct_scanner).
equipment_for(xray, xray_room).

protocol_max_parallel_patients(doctor, 1).
protocol_preemptable(Code) :- code_priority(Code, P) & P >= 1.
treatment_equipment(red, operating_room).
