// Generic FIPA ContractNet participant, used by hospitals and doctors.

+cfp(CnpId, Task)[source(Initiator)]
   <- .abolish(cfp(CnpId, Task));
      !bid_or_refuse(CnpId, Task, Initiator).

+!bid_or_refuse(CnpId, Task, Initiator)
   <- !make_bid(CnpId, Task, Bid);
      .send(Initiator, tell, propose(CnpId, Bid)).
-!bid_or_refuse(CnpId, Task, Initiator)
   <- .send(Initiator, tell, refuse(CnpId, not_capable)).

+accept_proposal(CnpId, Task)[source(Initiator)]
   <- .abolish(accept_proposal(CnpId, Task));
      !on_award(CnpId, Task).

+reject_proposal(CnpId)[source(Initiator)]
   <- .abolish(reject_proposal(CnpId)).
