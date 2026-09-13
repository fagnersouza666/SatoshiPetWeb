package br.com.satoshipet.api.pet.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Regras de nascimento, carência de 24h e retorno ao ovo (CC-12, CC-14, CA-011..013, CA-020..024).
 *
 * <p>Somente saldo confirmado nasce, reaparece ou zera a carência. Recebimento
 * pendente não é parâmetro destas funções.</p>
 */
public final class EggPolicy {

    public static final Duration GRACE = Duration.ofHours(24);

    private EggPolicy() {
    }

    /**
     * Próximo instante de início da carência de saldo confirmado zero.
     *
     * @param currentZeroSince início já gravado, ou nulo
     * @param confirmedSats    saldo confirmado on-chain
     * @param now              instante da avaliação
     * @return nulo se há saldo confirmado; {@code now} se a carência começa agora; senão o início atual
     */
    public static Instant nextZeroBalanceSince(Instant currentZeroSince, long confirmedSats, Instant now) {
        Objects.requireNonNull(now, "now");
        if (confirmedSats > 0L) {
            return null;
        }
        return currentZeroSince == null ? now : currentZeroSince;
    }

    /**
     * Carência de 24h decorrida (fronteira inclusiva).
     *
     * @param zeroBalanceSince início da carência, ou nulo
     * @param now              instante da avaliação
     * @return falso se a carência não começou; verdadeiro quando {@code now >= since + 24h}
     */
    public static boolean graceElapsed(Instant zeroBalanceSince, Instant now) {
        Objects.requireNonNull(now, "now");
        if (zeroBalanceSince == null) {
            return false;
        }
        return !now.isBefore(zeroBalanceSince.plus(GRACE));
    }

    /**
     * Nascimento ou reaparecimento exigem saldo confirmado positivo (CC-14).
     *
     * @param confirmedSats saldo confirmado on-chain
     * @return verdadeiro somente se {@code confirmedSats > 0}
     */
    public static boolean canAppear(long confirmedSats) {
        return confirmedSats > 0L;
    }

    /**
     * Fundamento único de nascimento perdido: pet já nascido, sem alimentação
     * VALID restante e saldo confirmado zero — ovo imediato, sem esperar 24h.
     *
     * @param alreadyBorn          {@code bornAt} já preenchido
     * @param anyValidFeedingLeft  existe outra alimentação VALID
     * @param confirmedSats        saldo confirmado on-chain
     * @return verdadeiro quando o retorno ao ovo é imediato
     */
    public static boolean immediateEggOnLostBirthFoundation(
            boolean alreadyBorn,
            boolean anyValidFeedingLeft,
            long confirmedSats
    ) {
        return alreadyBorn && !anyValidFeedingLeft && confirmedSats == 0L;
    }
}
