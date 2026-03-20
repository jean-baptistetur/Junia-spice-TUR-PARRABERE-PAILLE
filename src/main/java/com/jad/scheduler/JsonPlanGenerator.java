package com.jad.scheduler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jad.scheduler.model.MachineSchedule;
import com.jad.scheduler.model.ManufactureOrder;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JsonPlanGenerator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record OrderJson(int numOrder, int idProduct, double quantity) {}
    private record MachineScheduleJson(int idMachineTool, List<OrderJson> orders) {}

    public static String toJson(List<MachineSchedule> schedules) {
        List<MachineScheduleJson> result = new ArrayList<>();
        for (MachineSchedule s : schedules) {
            if (!s.getOrders().isEmpty()) {
                List<OrderJson> orders = new ArrayList<>();
                for (ManufactureOrder o : s.getOrders()) {
                    orders.add(new OrderJson(o.getNumOrder(), o.getIdProduct(), o.getQuantity()));
                }
                result.add(new MachineScheduleJson(s.getIdMachineTool(), orders));
            }
        }
        return GSON.toJson(result);
    }

    public static void saveToFile(List<MachineSchedule> schedules, String filePath) throws IOException {
        String json = toJson(schedules);
        FileWriter writer = new FileWriter(filePath);
        writer.write(json);
        writer.close();
        System.out.println("Fichier JSON généré : " + filePath);
    }
}