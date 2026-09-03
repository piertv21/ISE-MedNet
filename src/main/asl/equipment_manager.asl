+my_hospital(H)
   :  not ready
   <- .concat("equipment_manager_", H, Service);
      mednet.df.df_register(Service);
      +ready;
      .print("[", H, "] equipment manager online").

+request_equipment(ReqId, Equipment, Priority)[source(Doctor)]
   <- .abolish(request_equipment(ReqId, Equipment, Priority));
      ?tick(Now);
      +waiting_req(Equipment, req(ReqId, Doctor, Priority, Now));
      !try_grant(Equipment).

@grant[atomic]
+!try_grant(Equipment)
   :  equipment_state(Equipment, free) & not grant_out(Equipment, _, _)
      & .findall(w(Priority, Since, ReqId, Doctor),
                 waiting_req(Equipment, req(ReqId, Doctor, Priority, Since)),
                 Requests)
      & not .empty(Requests)
   <- .min(Requests, w(_, _, BestReq, BestDoctor));
      -waiting_req(Equipment, req(BestReq, BestDoctor, _, _));
      ?tick(Now);
      +grant_out(Equipment, BestReq, BestDoctor);
      +grant_tick(Equipment, Now);
      .print("granting ", Equipment, " to ", BestDoctor);
      .send(BestDoctor, tell, granted(BestReq, Equipment)).
+!try_grant(_) <- true.

+released(ReqId, Equipment)[source(Doctor)]
   <- .abolish(released(ReqId, Equipment));
      .abolish(grant_out(Equipment, ReqId, Doctor));
      .abolish(grant_tick(Equipment, _));
      !try_grant(Equipment).

+tick(Now)
   :  grant_out(Equipment, ReqId, Doctor) & grant_tick(Equipment, Granted)
      & Now - Granted > 10 & equipment_state(Equipment, free)
   <- .print("grant of ", Equipment, " to ", Doctor, " expired; revoking");
      .send(Doctor, tell, grant_revoked(ReqId, Equipment));
      .abolish(grant_out(Equipment, ReqId, Doctor));
      .abolish(grant_tick(Equipment, _));
      !try_grant(Equipment).

+lease_expired(Equipment, Owner)
   <- .print("lease expired: force-releasing ", Equipment, " held by ", Owner);
      force_release(Equipment);
      .abolish(grant_out(Equipment, _, _));
      .abolish(grant_tick(Equipment, _));
      !try_grant(Equipment).
