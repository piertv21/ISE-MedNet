package mednet.prolog;

import alice.tuprolog.Prolog;
import alice.tuprolog.SolveInfo;
import alice.tuprolog.Struct;
import alice.tuprolog.Term;
import alice.tuprolog.Theory;
import alice.tuprolog.exceptions.MalformedGoalException;
import alice.tuprolog.exceptions.NoSolutionException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PrologKb {

    private static final List<String> THEORY_RESOURCES = List.of(
            "/mednet_kb.pl",
            "/rbac.pl",
            "/strips.pl",
            "/care_domain.pl");

    private static final String SOURCE = readTheories();

    private static final ThreadLocal<Prolog> ENGINE = ThreadLocal.withInitial(PrologKb::newEngine);

    private PrologKb() {
    }

    public static boolean proves(final String goal) {
        return solve(goal).isSuccess();
    }

    public static Optional<Term> first(final String goal, final String variable) {
        final SolveInfo info = solve(goal);
        if (!info.isSuccess()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(info.getVarValue(variable)).map(Term::getTerm);
        } catch (final NoSolutionException e) {
            return Optional.empty();
        }
    }

    public static List<Term> all(final String template, final String goal) {
        final Term solutions = first("findall(" + template + ", (" + goal + "), Solutions)", "Solutions")
                .orElse(null);
        if (!(solutions instanceof Struct list) || !list.isList()) {
            return List.of();
        }
        final List<Term> result = new ArrayList<>();
        list.listIterator().forEachRemaining(term -> result.add(term.getTerm()));
        return result;
    }

    public static List<String> allAtoms(final String template, final String goal) {
        return all(template, goal).stream().map(PrologKb::text).toList();
    }

    public static Optional<Integer> firstInt(final String goal, final String variable) {
        return first(goal, variable)
                .filter(alice.tuprolog.Number.class::isInstance)
                .map(term -> ((alice.tuprolog.Number) term).intValue());
    }

    public static String text(final Term term) {
        final Term dereferenced = term.getTerm();
        if (dereferenced instanceof Struct struct && struct.isAtomic()) {
            return struct.getName();
        }
        return dereferenced.toString();
    }

    public static String quote(final String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static SolveInfo solve(final String goal) {
        try {
            return ENGINE.get().solve(goal + ".");
        } catch (final MalformedGoalException e) {
            throw new IllegalArgumentException("Malformed Prolog goal: " + goal, e);
        }
    }

    private static Prolog newEngine() {
        final Prolog engine = new Prolog();
        try {
            engine.setTheory(Theory.parseWithStandardOperators(SOURCE));
        } catch (final alice.tuprolog.exceptions.InvalidTheoryException e) {
            throw new IllegalStateException("Invalid MedNet Prolog theory", e);
        }
        return engine;
    }

    private static String readTheories() {
        final StringBuilder source = new StringBuilder();
        for (final String resource : THEORY_RESOURCES) {
            try (InputStream in = PrologKb.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("Prolog theory not on the classpath: " + resource);
                }
                source.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            } catch (final IOException e) {
                throw new IllegalStateException("Cannot read Prolog theory " + resource, e);
            }
        }
        return source.toString();
    }
}
