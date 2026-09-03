+!cnp_start(CnpId, Task, PreferredService, FallbackService)
   <- mednet.df.df_search(PreferredService, Preferred);
      if (.empty(Preferred)) {
         mednet.df.df_search(FallbackService, Found);
      } else {
         Found = Preferred;
      };
      .findall(A, (.member(A, Found) & not excluded(CnpId, A)), Eligible);
      !cnp_announce(CnpId, Task, Eligible).

+!cnp_announce(CnpId, Task, []) <- !cnp_no_winner(CnpId, Task).
+!cnp_announce(CnpId, Task, Participants)
   <- .print("[CNP] cfp ", CnpId, " -> ", Participants);
      .send(Participants, tell, cfp(CnpId, Task));
      ?cnp_deadline(Deadline);
      .wait(Deadline);
      !cnp_award(CnpId, Task).

+!cnp_award(CnpId, Task)
   <- .findall(offer(Cost, A), propose(CnpId, Cost)[source(A)], Offers);
      if (.empty(Offers)) {
         !cnp_cleanup(CnpId);
         !cnp_no_winner(CnpId, Task);
      } else {
         .min(Offers, offer(Best, Winner));
         .print("[CNP] ", CnpId, " awarded to ", Winner, " (bid ", Best, ")");
         .send(Winner, tell, accept_proposal(CnpId, Task));
         .findall(L, (propose(CnpId, _)[source(L)] & L \== Winner), Losers);
         .send(Losers, tell, reject_proposal(CnpId));
         !cnp_cleanup(CnpId);
         !cnp_awarded(CnpId, Winner, Task);
      }.

+!cnp_cleanup(CnpId)
   <- .abolish(propose(CnpId, _));
      .abolish(refuse(CnpId, _)).
