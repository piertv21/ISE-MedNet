max_plan_length(16).

plan(State0, Goal, Plan) :-
    max_plan_length(Budget),
    plan_from(State0, Goal, Budget, [State0], [], Reversed),
    reverse(Reversed, Plan),
    !.

plan_from(State, Goal, _, _, Acc, Acc) :-
    conditions_met(Goal, State).
plan_from(State, Goal, Budget, Seen, Acc, Plan) :-
    Budget > 0,
    Left is Budget - 1,
    action(Action, Preconditions, Add, Delete),
    conditions_met(Preconditions, State),
    apply_effects(State, Add, Delete, Next),
    \+ visited(Next, Seen),
    plan_from(Next, Goal, Left, [Next | Seen], [Action | Acc], Plan).

conditions_met([], _).
conditions_met([C | Cs], State) :-
    holds_in(C, State),
    conditions_met(Cs, State).

apply_effects(State, Add, Delete, Next) :-
    remove_all(Delete, State, Reduced),
    add_all(Add, Reduced, Next).

visited(State, [Seen | _]) :- same_state(State, Seen), !.
visited(State, [_ | Rest]) :- visited(State, Rest).

same_state(A, B) :- included(A, B), included(B, A).

included([], _).
included([X | Xs], S) :- contains(S, X), included(Xs, S).

contains([Y | _], X) :- X = Y, !.
contains([_ | T], X) :- contains(T, X).

remove_all([], State, State).
remove_all([X | Xs], State, Result) :-
    remove_every(X, State, Reduced),
    remove_all(Xs, Reduced, Result).

remove_every(_, [], []).
remove_every(X, [Y | T], Result) :- X == Y, !, remove_every(X, T, Result).
remove_every(X, [Y | T], [Y | Result]) :- remove_every(X, T, Result).

add_all([], State, State).
add_all([X | Xs], State, Result) :- contains(State, X), !, add_all(Xs, State, Result).
add_all([X | Xs], State, Result) :- add_all(Xs, [X | State], Result).
