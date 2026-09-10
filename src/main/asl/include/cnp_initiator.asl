// Generic FIPA ContractNet initiator, reused by control_center toward the hospitals and
// by triage_nurse toward the doctors. One round per negotiation at a time.
@cnp_start_round[atomic]
+!cnp_start(CnpId, Task, PreferredService, FallbackService)
   :  not cnp_running(CnpId)
   <- +cnp_running(CnpId);
      !!cnp_round(CnpId, Task, PreferredService, FallbackService).

+!cnp_start(CnpId, _, _, _)
   <- .print("[CNP] a round for ", CnpId, " is already in flight; request ignored").

// Layered DF lookup: ask the specialists first, and only fall back to the generic
// service if none is registered.
+!cnp_round(CnpId, Task, PreferredService, FallbackService)
   <- .df_search(PreferredService, Preferred);
      if (.empty(Preferred)) {
         .df_search(FallbackService, Found);
      } else {
         Found = Preferred;
      };
      .findall(A, (.member(A, Found) & not excluded(CnpId, A)), Eligible);
      !cnp_announce(CnpId, Task, Eligible).

// A round that breaks down must still release the negotiation, or the CnpId stays
// blocked and no later trigger can reopen it.
-!cnp_round(CnpId, Task, _, _)
   <- !cnp_cleanup(CnpId);
      !cnp_no_winner(CnpId, Task).

+!cnp_announce(CnpId, Task, [])
   <- !cnp_cleanup(CnpId);
      !cnp_no_winner(CnpId, Task).
+!cnp_announce(CnpId, Task, Participants)
   <- .print("[CNP] cfp ", CnpId, " -> ", Participants);
      // A participant answering after the previous deadline leaves its bid behind, and
      // this round must not count it: the announcement opens a clean slate.
      .abolish(propose(CnpId, _));
      .abolish(refuse(CnpId, _));
      .send(Participants, tell, cfp(CnpId, Task));
      ?cnp_deadline(Deadline);
      .wait(Deadline);                       // asynchronous bid collection
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

// Forget this round's bids so a later round for the same CnpId starts clean, and release
// the negotiation. Always called before the award or no-winner callback, so a retry
// issued from inside the callback opens a fresh round instead of being ignored.
+!cnp_cleanup(CnpId)
   <- .abolish(propose(CnpId, _));
      .abolish(refuse(CnpId, _));
      .abolish(cnp_running(CnpId)).