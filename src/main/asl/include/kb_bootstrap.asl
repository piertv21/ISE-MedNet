// The medical and organizational constraints are written in Prolog. This plan pulls them
// into the belief base once, so plan contexts can read them as ordinary beliefs.

+!load_medical_kb
   :  not kb_loaded
   <- mednet.prolog.consult_kb(Facts);
      for (.member(Fact, Facts)) { +Fact };
      +kb_loaded.

+!load_medical_kb.   // already internalized