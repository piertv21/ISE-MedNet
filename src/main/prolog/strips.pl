% A generic STRIPS planner. It knows nothing about hospitals, only about the action
% schemas action(Name, Preconditions, AddList, DeleteList) and the test
% holds_in(Condition, State), both supplied by the domain theory in care_domain.pl.

% The longest plan the planner will consider before giving up.
max_plan_length(16).

% plan(+InitialState, +Goal, -Plan): the first plan found, or failure if the goal is not
% reachable within max_plan_length/1.
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

% The effects of an action: delete first, then add, following the STRIPS convention.
apply_effects(State, Add, Delete, Next) :-
    remove_all(Delete, State, Reduced),
    add_all(Add, Reduced, Next).

% States are unordered sets of ground fluents, so equality is mutual inclusion.
visited(State, [Seen | _]) :- same_state(State, Seen), !.
visited(State, [_ | Rest]) :- visited(State, Rest).

same_state(A, B) :- included(A, B), included(B, A).

included([], _).
included([X | Xs], S) :- contains(S, X), included(Xs, S).

% contains/2 is memberchk/2 with the arguments swapped (tuProlog has no memberchk/2).
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
