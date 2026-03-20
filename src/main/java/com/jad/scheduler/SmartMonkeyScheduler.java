package com.jad.scheduler;

import com.jad.connector.DBConnector;
import com.jad.entity.MachineTool;
import com.jad.entity.OperationType;
import com.jad.entity.Product;
import com.jad.entity.ProductRecipe;
import com.jad.entity.RecipeLine;
import com.jad.scheduler.model.MachineSchedule;
import com.jad.scheduler.model.ManufactureOrder;
import com.jad.service.OperationTypeService;
import com.jad.service.ProductRecipeService;
import com.jad.service.ProductService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SmartMonkeyScheduler {

    private final ProductService productService;
    private final ProductRecipeService productRecipeService;
    private final OperationTypeService operationTypeService;

    private final Map<Integer, MachineSchedule> scheduleMap = new HashMap<>();
    private final Map<Integer, Double> quantityMemo = new HashMap<>();

    public SmartMonkeyScheduler(DBConnector dbConnector) throws SQLException {
        this.productService = new ProductService(dbConnector);
        this.productRecipeService = new ProductRecipeService(dbConnector);
        this.operationTypeService = new OperationTypeService(dbConnector);
    }

    public List<MachineSchedule> schedule(int idProduct, double quantity) throws SQLException {
        scheduleMap.clear();
        quantityMemo.clear();
        accumulateQuantities(idProduct, quantity);
        planInOrder(idProduct, new HashSet<>());
        return new ArrayList<>(scheduleMap.values());
    }

    private void accumulateQuantities(int idProduct, double quantity) throws SQLException {
        Product product = productService.getById(idProduct);
        if (product == null || Boolean.TRUE.equals(product.getIsAtomic())) {
            return;
        }

        ProductRecipe recipe = productRecipeService.getByIdProduct(idProduct);
        if (recipe == null || recipe.getIdProduct() == null) {
            return;
        }

        OperationType op = operationTypeService.getById(recipe.getIdOperationType());
        if (op == null) {
            return;
        }

        double lossRate = op.getLossOfQuantity() / 100.0;
        double gross = quantity / (1.0 - lossRate);

        if (quantityMemo.containsKey(idProduct)) {
            quantityMemo.put(idProduct, quantityMemo.get(idProduct) + gross);
        } else {
            quantityMemo.put(idProduct, gross);
        }

        for (RecipeLine line : recipe.getRecipeLines()) {
            double componentQty = gross * (line.getPercentage() / 100.0);
            accumulateQuantities(line.getIdComponent(), componentQty);
        }
    }

    private void planInOrder(int idProduct, Set<Integer> planned) throws SQLException {
        if (planned.contains(idProduct)) {
            return;
        }

        Product product = productService.getById(idProduct);
        if (product == null || Boolean.TRUE.equals(product.getIsAtomic())) {
            planned.add(idProduct);
            return;
        }

        ProductRecipe recipe = productRecipeService.getByIdProduct(idProduct);
        if (recipe == null || recipe.getIdProduct() == null) {
            planned.add(idProduct);
            return;
        }

        for (RecipeLine line : recipe.getRecipeLines()) {
            planInOrder(line.getIdComponent(), planned);
        }

        planned.add(idProduct);

        OperationType op = operationTypeService.getById(recipe.getIdOperationType());
        if (op == null) {
            return;
        }

        int nbComponents = recipe.getRecipeLines().size();
        if (nbComponents < op.getMinNbComponents() || nbComponents > op.getMaxNbComponents()) {
            System.err.println("[Singe] Composants invalides pour : " + op.getLabel());
            return;
        }

        List<MachineTool> machines = operationTypeService.getMachineToolsForOperationTypeId(recipe.getIdOperationType());
        if (machines.isEmpty()) {
            System.err.println("[Singe] Aucune machine pour : " + op.getLabel());
            return;
        }

        double remaining;
        if (quantityMemo.containsKey(idProduct)) {
            remaining = quantityMemo.get(idProduct);
        } else {
            remaining = 0.0;
        }

        int localOrder = 0;

        while (remaining > 0) {
            MachineTool chosen = machines.get(0);
            double minLoad = Double.MAX_VALUE;

            for (MachineTool m : machines) {
                double load = 0.0;
                if (scheduleMap.containsKey(m.getId())) {
                    load = scheduleMap.get(m.getId()).getTotalLoad();
                }
                if (load < minLoad) {
                    minLoad = load;
                    chosen = m;
                }
            }

            double lot = Math.min(remaining, chosen.getMaxQuantity());
            if (lot < chosen.getMinQuantity()) {
                lot = chosen.getMinQuantity();
            }
            remaining = remaining - lot;

            if (!scheduleMap.containsKey(chosen.getId())) {
                scheduleMap.put(chosen.getId(), new MachineSchedule(chosen.getId()));
            }
            scheduleMap.get(chosen.getId()).addOrder(new ManufactureOrder(localOrder, idProduct, lot));
            localOrder = localOrder + 1;

            System.out.println("[Singe] " + product.getLabel() + " x" + lot + " → " + chosen.getLabel());
        }
    }
}