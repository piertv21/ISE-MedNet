+!load_medical_kb
   :  not kb_loaded
   <- mednet.prolog.consult_kb(Facts);
      for (.member(Fact, Facts)) { +Fact };
      +kb_loaded.

+!load_medical_kb.