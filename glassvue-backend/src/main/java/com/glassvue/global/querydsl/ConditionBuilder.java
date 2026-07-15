package com.glassvue.global.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.PathBuilder;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import org.springframework.util.StringUtils;

/**
 * 검색 record의 @Cond 애노테이션을 읽어 QueryDSL 조건(BooleanBuilder)을 자동 생성한다.
 * - 값이 null / 빈 문자열 / 빈 컬렉션이면 그 조건은 제외(동적 검색).
 * - 조인 · OR 그룹 등 복잡한 조건은 이걸로 억지로 만들지 말고 Impl에서 순수 QueryDSL로 작성(탈출구).
 *
 * 사용: BooleanBuilder where = ConditionBuilder.of(QNotice.notice, searchDto).build();
 */
public final class ConditionBuilder {

    private final PathBuilder<?> root;
    private final Object search;

    private ConditionBuilder(EntityPathBase<?> qRoot, Object search) {
        // 쿼리 from 절의 Q와 같은 metadata(별칭)로 PathBuilder를 만들어 조건이 같은 루트를 가리키게 한다.
        this.root = new PathBuilder<>(qRoot.getType(), qRoot.getMetadata());
        this.search = search;
    }

    public static ConditionBuilder of(EntityPathBase<?> qRoot, Object search) {
        return new ConditionBuilder(qRoot, search);
    }

    public BooleanBuilder build() {
        BooleanBuilder builder = new BooleanBuilder();
        if (search == null) {
            return builder;
        }
        for (RecordComponent rc : search.getClass().getRecordComponents()) {
            Cond cond = rc.getAnnotation(Cond.class);
            if (cond == null) {
                continue;
            }
            Object value = read(rc);
            if (isEmpty(value)) {
                continue;
            }
            String path = cond.path().isBlank() ? rc.getName() : cond.path();
            Object converted = cond.transform().apply(value);
            builder.and(express(path, cond.op(), converted));
        }
        return builder;
    }

    private Object read(RecordComponent rc) {
        try {
            return rc.getAccessor().invoke(search);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("검색 필드 접근 실패: " + rc.getName(), e);
        }
    }

    private static boolean isEmpty(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof String s) {
            return !StringUtils.hasText(s);
        }
        if (v instanceof Collection<?> c) {
            return c.isEmpty();
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private BooleanExpression express(String path, Op op, Object value) {
        return switch (op) {
            case EQ -> root.get(path).eq(value);
            case NE -> root.get(path).ne(value);
            case IN -> root.get(path).in((Collection) value);
            case CONTAINS -> root.getString(path).containsIgnoreCase((String) value);
            case STARTS -> root.getString(path).startsWithIgnoreCase((String) value);
            case ENDS -> root.getString(path).endsWithIgnoreCase((String) value);
            case GOE -> root.getComparable(path, (Class) value.getClass()).goe((Comparable) value);
            case GT -> root.getComparable(path, (Class) value.getClass()).gt((Comparable) value);
            case LOE -> root.getComparable(path, (Class) value.getClass()).loe((Comparable) value);
            case LT -> root.getComparable(path, (Class) value.getClass()).lt((Comparable) value);
        };
    }
}
