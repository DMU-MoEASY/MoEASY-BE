package com.moeasy.moeasybe.domain.settlement.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

/** 이미 적용된 SQL의 체크섬을 변경하지 않고 납부 마이그레이션의 첫 DDL 전에 검사한다. */
@Component
public class SettlementPaymentMigrationGuard extends BaseCallback {

    private static final MigrationVersion VERSION = MigrationVersion.fromVersion("20261030111343");

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_EACH_MIGRATE
                && context.getMigrationInfo() != null
                && VERSION.equals(context.getMigrationInfo().getVersion());
    }

    @Override
    public void handle(Event event, Context context) {
        try (var statement = context.getConnection().createStatement();
                var rows =
                        statement.executeQuery(
                                """
                                SELECT EXISTS (
                                    SELECT 1 FROM settlement_payment WHERE amount > 9223372036854775807
                                )
                                """)) {
            if (rows.next() && rows.getBoolean(1)) {
                throw new FlywayException(
                        "납부 금액이 Java Long 범위를 초과합니다. 원본 데이터를 확인·정정한 후 "
                                + VERSION
                                + " 마이그레이션을 다시 실행하세요. 테이블 변경 전에 중단했습니다.");
            }
        } catch (SQLException ex) {
            throw new FlywayException("납부 금액 사전 검사에 실패했습니다. 테이블 변경을 중단합니다.", ex);
        }
    }
}
