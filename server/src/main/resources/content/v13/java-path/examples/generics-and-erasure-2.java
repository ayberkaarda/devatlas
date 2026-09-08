import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;

final class WhatSurvivesErasure {

    static final class Holder {
        List<String> names = List.of();
        List<Integer> counts = List.of();
        Map<String, List<Integer>> index = Map.of();

        static List<String> pick(Map<String, Integer> from) {
            return List.copyOf(from.keySet());
        }
    }

    public static void main(String[] args) throws Exception {
        Field names = Holder.class.getDeclaredField("names");
        System.out.println("erased field type   : " + names.getType().getName());
        System.out.println("declared field type : " + names.getGenericType());

        Field index = Holder.class.getDeclaredField("index");
        System.out.println("declared field type : " + index.getGenericType());
        ParameterizedType parameterized = (ParameterizedType) index.getGenericType();
        System.out.println("second argument     : " + parameterized.getActualTypeArguments()[1]);

        Method pick = Holder.class.getDeclaredMethod("pick", Map.class);
        System.out.println("erased return type  : " + pick.getReturnType().getName());
        System.out.println("declared return type: " + pick.getGenericReturnType());
        System.out.println("declared parameter  : " + pick.getGenericParameterTypes()[0]);

        // Two fields with the same erasure and different declared types.
        Field counts = Holder.class.getDeclaredField("counts");
        System.out.println("erasures equal      : " + names.getType().equals(counts.getType()));
        System.out.println("declarations equal  : " + names.getGenericType().equals(counts.getGenericType()));
    }
}
