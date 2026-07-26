package com.runcat;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SwingCat {

    private static JWindow window;
    private static JLabel catLabel;
    private static int currentFrame = 0;
    private static long lastFrameTime = 0;
    private static OperatingSystemMXBean bean;
    private static MemoryMXBean memoryMXBean;
    private static double currentCpu = 0;
    private static double currentMemory = 0;
    private static double currentSelfMemory = 0;

    // 告警相关
    private static double cpuWarningThreshold = 80;
    private static double memoryWarningThreshold = 85;
    private static long lastCpuWarningTime = 0;
    private static long lastMemoryWarningTime = 0;
    private static final long WARNING_COOLDOWN = 30000;

    // 悬浮窗
    private static JWindow tooltipWindow;
    private static JLabel tooltipLabel;
    private static boolean mouseInside = false;

    // 右键菜单
    private static JPopupMenu popupMenu;

    // 多宠物支持
    private static String currentPet = "cat";
    private static Map<String, ImageIcon[][]> allPetFrames = new HashMap<>();
    private static Map<String, Integer> petActualFrameCount = new HashMap<>();
    private static final String[] PET_TYPES = {"cat", "dog"};
    private static final String[] PET_NAMES = {"🐱 小猫", "🐶 小狗"};

    // 历史数据
    private static List<Double> cpuHistory = new ArrayList<>();
    private static List<Double> memoryHistory = new ArrayList<>();
    private static String currentDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

    // 大小控制
    private static int currentSize = 96;
    private static final int[] SIZE_OPTIONS = {48, 64, 80, 96, 128, 160, 192, 224, 256};

    // 动画控制
    private static boolean animationPaused = false;
    private static javax.swing.Timer animationTimer;

    // 拖拽相关
    private static Point dragStartPoint;
    private static boolean isDragging = false;

    // 控制中心窗口
    private static JDialog controlCenterDialog = null;

    public static void main(String[] args) {
        loadAllPetImages();

        bean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        memoryMXBean = ManagementFactory.getMemoryMXBean();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                double cpu = bean.getCpuLoad() * 100;
                if (cpu >= 0 && cpu <= 100) {
                    currentCpu = cpu;
                    cpuHistory.add(cpu);
                    if (cpuHistory.size() > 3600) cpuHistory.remove(0);
                    checkCpuWarning(cpu);
                }

                long totalMemory = bean.getTotalPhysicalMemorySize();
                long freeMemory = bean.getFreePhysicalMemorySize();
                double memoryUsage = (double) (totalMemory - freeMemory) / totalMemory * 100;
                if (memoryUsage >= 0 && memoryUsage <= 100) {
                    currentMemory = memoryUsage;
                    memoryHistory.add(memoryUsage);
                    if (memoryHistory.size() > 3600) memoryHistory.remove(0);
                    checkMemoryWarning(memoryUsage);
                }

                MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
                long usedHeap = heapUsage.getUsed();
                currentSelfMemory = usedHeap / (1024.0 * 1024.0);

                System.out.printf("CPU: %.1f%%  系统内存: %.1f%%  自身内存: %.2f MB%n", currentCpu, currentMemory, currentSelfMemory);

                if (mouseInside && tooltipWindow != null && tooltipWindow.isVisible()) {
                    updateTooltipContentAndPosition();
                }

                String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                if (!today.equals(currentDate)) {
                    currentDate = today;
                    cpuHistory.clear();
                    memoryHistory.clear();
                    System.out.println("新的一天，历史数据已重置");
                }
            } catch (Exception e) {
                System.out.println("监控错误: " + e.getMessage());
            }
        }, 0, 1, TimeUnit.SECONDS);

        window = new JWindow();
        window.setBackground(new Color(0, 0, 0, 0));

        catLabel = new JLabel();
        catLabel.setPreferredSize(new Dimension(currentSize, currentSize));
        catLabel.setHorizontalAlignment(SwingConstants.CENTER);
        catLabel.setVerticalAlignment(SwingConstants.CENTER);

        updatePetImage(0);

        window.getContentPane().add(catLabel);
        window.pack();
        window.setAlwaysOnTop(true);
        setWindowPosition();
        window.setVisible(true);

        createTooltipWindow();
        createPopupMenu();
        setupMouseEvents();
        startAnimation();
    }

    // 高质量缩放图片
    private static ImageIcon scaleImageHighQuality(Image original, int targetSize) {
        BufferedImage bi = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(original, 0, 0, targetSize, targetSize, null);
        g2d.dispose();
        return new ImageIcon(bi);
    }

    private static ImageIcon loadImageDirect(String path, int targetSize) {
        URL url = SwingCat.class.getResource(path);
        if (url == null) return null;

        ImageIcon original = new ImageIcon(url);
        int originalWidth = original.getIconWidth();
        int originalHeight = original.getIconHeight();

        if (originalWidth == targetSize && originalHeight == targetSize) {
            return original;
        }

        return scaleImageHighQuality(original.getImage(), targetSize);
    }

    private static void loadAllPetImages() {
        for (String pet : PET_TYPES) {
            List<ImageIcon> originalFrames = new ArrayList<>();
            int frameIndex = 1;
            while (true) {
                String path = "/images/" + pet + "/" + pet + "_" + frameIndex + ".png";
                ImageIcon img = loadImageDirect(path, currentSize);
                if (img != null) {
                    originalFrames.add(img);
                    System.out.println("加载: " + pet + "_" + frameIndex);
                    frameIndex++;
                } else {
                    break;
                }
            }

            int actualCount = originalFrames.size();
            petActualFrameCount.put(pet, actualCount);
            if (actualCount == 0) {
                for (int i = 0; i < 4; i++) {
                    originalFrames.add(createPlaceholder(i, pet, currentSize));
                }
                actualCount = 4;
                petActualFrameCount.put(pet, 4);
            }

            ImageIcon[][] sizeFrames = new ImageIcon[SIZE_OPTIONS.length][4];
            for (int s = 0; s < SIZE_OPTIONS.length; s++) {
                int size = SIZE_OPTIONS[s];
                for (int i = 0; i < 4; i++) {
                    int idx = i % actualCount;
                    ImageIcon original = originalFrames.get(idx);
                    if (original.getIconWidth() != size) {
                        sizeFrames[s][i] = scaleImageHighQuality(original.getImage(), size);
                    } else {
                        sizeFrames[s][i] = original;
                    }
                }
            }
            allPetFrames.put(pet, sizeFrames);
        }
    }

    private static ImageIcon getCurrentPetFrame(int frame) {
        ImageIcon[][] frames = allPetFrames.get(currentPet);
        int sizeIndex = 0;
        for (int i = 0; i < SIZE_OPTIONS.length; i++) {
            if (SIZE_OPTIONS[i] == currentSize) {
                sizeIndex = i;
                break;
            }
        }
        return frames[sizeIndex][frame % 4];
    }

    private static void updatePetImage(int frame) {
        Point oldPos = window.getLocation();
        catLabel.setIcon(getCurrentPetFrame(frame));
        catLabel.setPreferredSize(new Dimension(currentSize, currentSize));
        window.pack();
        window.setLocation(oldPos);
    }

    private static void startAnimation() {
        animationTimer = new javax.swing.Timer(50, e -> {
            if (!animationPaused) {
                int delay = currentCpu < 10 ? 400 : (currentCpu < 30 ? 200 : (currentCpu < 60 ? 100 : 50));
                animationTimer.setDelay(delay);
                currentFrame = (currentFrame + 1) % 4;
                updatePetImage(currentFrame);
            }
        });
        animationTimer.start();
    }

    private static void createPopupMenu() {
        popupMenu = new JPopupMenu();

        JMenuItem statsItem = new JMenuItem("📊 查看统计");
        statsItem.addActionListener(e -> showStatistics());

        JMenuItem thresholdItem = new JMenuItem("⚙️ 告警阈值设置");
        thresholdItem.addActionListener(e -> showThresholdSettings());

        JMenuItem selfMemItem = new JMenuItem("💾 查看自身内存");
        selfMemItem.addActionListener(e -> showSelfMemory());

        JMenuItem infoPanelItem = new JMenuItem("🖥️ 控制中心");
        infoPanelItem.addActionListener(e -> showInfoPanel());

        JMenu switchMenu = new JMenu("🐾 切换宠物");
        ButtonGroup petGroup = new ButtonGroup();
        for (int i = 0; i < PET_TYPES.length; i++) {
            String pet = PET_TYPES[i];
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(PET_NAMES[i]);
            if (pet.equals(currentPet)) item.setSelected(true);
            item.addActionListener(ev -> {
                currentPet = pet;
                currentFrame = 0;
                updatePetImage(0);
            });
            petGroup.add(item);
            switchMenu.add(item);
        }

        JMenuItem pauseItem = new JMenuItem("⏸ 暂停动画");
        pauseItem.addActionListener(e -> {
            animationPaused = !animationPaused;
            pauseItem.setText(animationPaused ? "▶ 恢复动画" : "⏸ 暂停动画");
        });

        JMenu sizeMenu = new JMenu("🔍 宠物大小");
        ButtonGroup sizeGroup = new ButtonGroup();
        for (int size : SIZE_OPTIONS) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(size + "px");
            if (size == currentSize) item.setSelected(true);
            item.addActionListener(e -> {
                currentSize = size;
                updatePetImage(currentFrame);
            });
            sizeGroup.add(item);
            sizeMenu.add(item);
        }

        JMenuItem resetItem = new JMenuItem("📍 重置位置");
        resetItem.addActionListener(e -> setWindowPosition());

        JMenuItem exitItem = new JMenuItem("❌ 退出");
        exitItem.addActionListener(e -> System.exit(0));

        popupMenu.add(statsItem);
        popupMenu.add(thresholdItem);
        popupMenu.add(selfMemItem);
        popupMenu.addSeparator();
        popupMenu.add(infoPanelItem);
        popupMenu.add(switchMenu);
        popupMenu.addSeparator();
        popupMenu.add(pauseItem);
        popupMenu.add(sizeMenu);
        popupMenu.addSeparator();
        popupMenu.add(resetItem);
        popupMenu.add(exitItem);
    }

    // 显示控制中心大界面（简化版，避免类型转换问题）
    private static void showInfoPanel() {
        if (controlCenterDialog != null && controlCenterDialog.isVisible()) {
            controlCenterDialog.toFront();
            return;
        }

        // 计算统计数据
        double avgCpu = 0, maxCpu = 0;
        double avgMem = 0, maxMem = 0;
        if (!cpuHistory.isEmpty()) {
            for (double c : cpuHistory) { avgCpu += c; if (c > maxCpu) maxCpu = c; }
            for (double m : memoryHistory) { avgMem += m; if (m > maxMem) maxMem = m; }
            avgCpu /= cpuHistory.size();
            avgMem /= memoryHistory.size();
        }

        JDialog dialog = new JDialog();
        dialog.setTitle("🐱 任务栏宠物系统 - 控制中心");
        dialog.setModal(false);
        dialog.setAlwaysOnTop(true);
        dialog.setSize(650, 700);
        dialog.setLocationRelativeTo(window);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        controlCenterDialog = dialog;

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(15, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(new Color(255, 248, 235));

        // 标题
        JLabel titleLabel = new JLabel("🐱 任务栏宠物系统", JLabel.CENTER);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 28));
        titleLabel.setForeground(new Color(210, 105, 30));
        JLabel subTitleLabel = new JLabel("系统负载可视化工具 | 让监控更有趣", JLabel.CENTER);
        subTitleLabel.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        subTitleLabel.setForeground(new Color(160, 120, 80));
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setBackground(new Color(255, 248, 235));
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(8));
        titlePanel.add(subTitleLabel);

        // 仪表盘
        JPanel dashboardPanel = new JPanel();
        dashboardPanel.setLayout(new GridLayout(1, 3, 20, 0));
        dashboardPanel.setBackground(new Color(255, 248, 235));
        dashboardPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(255, 200, 150), 2),
                "📊 实时负载仪表盘", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("微软雅黑", Font.BOLD, 14), new Color(210, 105, 30)
        ));
        dashboardPanel.add(createGaugeCard("💻 CPU 使用率", currentCpu, new Color(255, 140, 100)));
        dashboardPanel.add(createGaugeCard("🧠 系统内存", currentMemory, new Color(100, 180, 150)));
        dashboardPanel.add(createGaugeCard("📌 程序自身", currentSelfMemory, new Color(150, 120, 200)));

        // 统计区域
        JPanel statsPanel = new JPanel();
        statsPanel.setLayout(new GridLayout(2, 3, 15, 15));
        statsPanel.setBackground(new Color(255, 248, 235));
        statsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(255, 200, 150), 2),
                "📈 今日统计", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("微软雅黑", Font.BOLD, 14), new Color(210, 105, 30)
        ));
        statsPanel.add(createStatCard("📊 CPU 平均", String.format("%.1f%%", avgCpu), new Color(255, 140, 100)));
        statsPanel.add(createStatCard("📈 CPU 最高", String.format("%.1f%%", maxCpu), new Color(255, 140, 100)));
        statsPanel.add(createStatCard("📊 内存平均", String.format("%.1f%%", avgMem), new Color(100, 180, 150)));
        statsPanel.add(createStatCard("📈 内存最高", String.format("%.1f%%", maxMem), new Color(100, 180, 150)));
        statsPanel.add(createStatCard("📋 记录条数", cpuHistory.size() + " 条", new Color(150, 120, 200)));
        statsPanel.add(createStatCard("🔔 告警阈值", String.format("CPU>%.0f%% MEM>%.0f%%", cpuWarningThreshold, memoryWarningThreshold), new Color(255, 180, 100)));

        // 宠物状态
        JPanel petStatusPanel = new JPanel();
        petStatusPanel.setLayout(new GridLayout(1, 2, 15, 0));
        petStatusPanel.setBackground(new Color(255, 248, 235));
        petStatusPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(255, 200, 150), 2),
                "🐾 宠物小窝", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("微软雅黑", Font.BOLD, 14), new Color(210, 105, 30)
        ));
        petStatusPanel.add(createInfoCard("🏠 当前宠物", currentPet.equals("cat") ? "🐱 小橘猫" : "🐶 小黄狗"));
        petStatusPanel.add(createInfoCard("📏 宠物大小", currentSize + " 像素"));

        // 项目信息
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new GridLayout(2, 3, 10, 10));
        infoPanel.setBackground(new Color(255, 248, 235));
        infoPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(255, 200, 150), 2),
                "ℹ️ 关于项目", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("微软雅黑", Font.BOLD, 14), new Color(210, 105, 30)
        ));
        infoPanel.add(createInfoCard("📦 项目名称", "任务栏宠物系统"));
        infoPanel.add(createInfoCard("🔖 版本", "v2.0 温馨版"));
        infoPanel.add(createInfoCard("☕ 开发语言", "Java 21"));
        infoPanel.add(createInfoCard("⚙️ 技术栈", "Swing / 多线程"));
        infoPanel.add(createInfoCard("🎯 核心功能", "监控 / 动画 / 告警"));
        infoPanel.add(createInfoCard("💿 运行方式", "独立 exe"));

        // 底部按钮
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(new Color(255, 248, 235));
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 10));
        JButton closeBtn = new JButton("✖️ 关闭");
        closeBtn.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        closeBtn.setBackground(new Color(160, 120, 80));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setFocusPainted(false);
        closeBtn.setPreferredSize(new Dimension(110, 35));
        closeBtn.addActionListener(e -> dialog.dispose());
        buttonPanel.add(closeBtn);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(new Color(255, 248, 235));
        centerPanel.add(dashboardPanel);
        centerPanel.add(Box.createVerticalStrut(15));
        centerPanel.add(statsPanel);
        centerPanel.add(Box.createVerticalStrut(15));
        centerPanel.add(petStatusPanel);
        centerPanel.add(Box.createVerticalStrut(15));
        centerPanel.add(infoPanel);

        mainPanel.add(titlePanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel);
        dialog.setVisible(true);
    }

    // 创建仪表卡片
    private static JPanel createGaugeCard(String title, double value, Color color) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 220, 180), 1),
                BorderFactory.createEmptyBorder(15, 12, 15, 12)
        ));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        JLabel valueLabel = new JLabel(String.format("%.1f%%", value));
        valueLabel.setFont(new Font("微软雅黑", Font.BOLD, 22));
        valueLabel.setForeground(color);
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setValue((int) Math.min(value, 100));
        progressBar.setForeground(color);
        progressBar.setBackground(new Color(255, 235, 215));
        progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(new Dimension(100, 10));
        JPanel valuePanel = new JPanel(new BorderLayout());
        valuePanel.setBackground(Color.WHITE);
        valuePanel.add(valueLabel, BorderLayout.CENTER);
        valuePanel.add(progressBar, BorderLayout.SOUTH);
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valuePanel, BorderLayout.CENTER);
        return card;
    }

    // 创建统计卡片
    private static JPanel createStatCard(String label, String value, Color color) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 220, 180), 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
        JLabel leftLabel = new JLabel(label);
        leftLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        JLabel rightLabel = new JLabel(value);
        rightLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        rightLabel.setForeground(color);
        rightLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        card.add(leftLabel, BorderLayout.WEST);
        card.add(rightLabel, BorderLayout.EAST);
        return card;
    }

    // 创建信息卡片
    private static JPanel createInfoCard(String label, String value) {
        JPanel card = new JPanel(new BorderLayout(5, 0));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 220, 180), 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        JLabel leftLabel = new JLabel(label);
        leftLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        JLabel rightLabel = new JLabel(value);
        rightLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        rightLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        card.add(leftLabel, BorderLayout.WEST);
        card.add(rightLabel, BorderLayout.EAST);
        return card;
    }

    private static void checkCpuWarning(double cpu) {
        if (cpu >= cpuWarningThreshold) {
            long now = System.currentTimeMillis();
            if (now - lastCpuWarningTime > WARNING_COOLDOWN) {
                lastCpuWarningTime = now;
                showWarning("⚠️ CPU 过高告警", String.format("当前 CPU 使用率: %.1f%%\n阈值: %.0f%%", cpu, cpuWarningThreshold));
            }
        }
    }

    private static void checkMemoryWarning(double memory) {
        if (memory >= memoryWarningThreshold) {
            long now = System.currentTimeMillis();
            if (now - lastMemoryWarningTime > WARNING_COOLDOWN) {
                lastMemoryWarningTime = now;
                showWarning("⚠️ 内存过高告警", String.format("当前系统内存使用率: %.1f%%\n阈值: %.0f%%", memory, memoryWarningThreshold));
            }
        }
    }

    private static void showWarning(String title, String message) {
        SwingUtilities.invokeLater(() -> {
            JDialog dialog = new JDialog();
            dialog.setTitle(title);
            dialog.setModal(true);
            dialog.setAlwaysOnTop(true);
            dialog.setSize(320, 160);
            dialog.setLocationRelativeTo(window);
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            panel.setBackground(new Color(255, 248, 235));
            JLabel iconLabel = new JLabel("⚠️", JLabel.CENTER);
            iconLabel.setFont(new Font("Segoe UI", Font.PLAIN, 40));
            JTextArea msgArea = new JTextArea(message);
            msgArea.setEditable(false);
            msgArea.setBackground(panel.getBackground());
            msgArea.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            msgArea.setWrapStyleWord(true);
            msgArea.setLineWrap(true);
            JButton okButton = new JButton("知道了");
            okButton.setBackground(new Color(210, 105, 30));
            okButton.setForeground(Color.WHITE);
            okButton.setFocusPainted(false);
            okButton.addActionListener(e -> dialog.dispose());
            JPanel buttonPanel = new JPanel();
            buttonPanel.setBackground(new Color(255, 248, 235));
            buttonPanel.add(okButton);
            panel.add(iconLabel, BorderLayout.NORTH);
            panel.add(msgArea, BorderLayout.CENTER);
            panel.add(buttonPanel, BorderLayout.SOUTH);
            dialog.add(panel);
            dialog.setVisible(true);
        });
    }

    private static void showThresholdSettings() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JSpinner cpuSpinner = new JSpinner(new SpinnerNumberModel((int) cpuWarningThreshold, 30, 100, 5));
        JSpinner memSpinner = new JSpinner(new SpinnerNumberModel((int) memoryWarningThreshold, 30, 100, 5));
        panel.add(new JLabel("CPU 告警阈值 (%):"));
        panel.add(cpuSpinner);
        panel.add(new JLabel("内存告警阈值 (%):"));
        panel.add(memSpinner);
        int result = JOptionPane.showConfirmDialog(window, panel, "⚙️ 告警阈值设置",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            cpuWarningThreshold = (Integer) cpuSpinner.getValue();
            memoryWarningThreshold = (Integer) memSpinner.getValue();
            JOptionPane.showMessageDialog(window, "告警阈值已更新\nCPU: " + (int) cpuWarningThreshold + "%\n内存: " + (int) memoryWarningThreshold + "%", "设置成功", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static void createTooltipWindow() {
        tooltipWindow = new JWindow();
        tooltipWindow.setAlwaysOnTop(true);
        tooltipLabel = new JLabel();
        tooltipLabel.setOpaque(true);
        tooltipLabel.setBackground(new Color(40, 40, 40, 230));
        tooltipLabel.setForeground(Color.WHITE);
        tooltipLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        tooltipLabel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        tooltipWindow.getContentPane().add(tooltipLabel);
        tooltipWindow.pack();
        catLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                mouseInside = true;
                updateTooltipContentAndPosition();
                tooltipWindow.setVisible(true);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                mouseInside = false;
                tooltipWindow.setVisible(false);
            }
        });
        catLabel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (mouseInside) updateTooltipPosition();
            }
        });
    }

    private static void updateTooltipContentAndPosition() {
        if (!mouseInside) return;
        String text = String.format("<html><div style='text-align:center;'>💻 CPU: %.0f%%<br>🧠 内存: %.0f%%<br>📌 自身: %.1f MB</div></html>",
                currentCpu, currentMemory, currentSelfMemory);
        tooltipLabel.setText(text);
        tooltipWindow.pack();
        updateTooltipPosition();
    }

    private static void updateTooltipPosition() {
        if (!mouseInside) return;
        Point catPos = window.getLocation();
        int x = catPos.x + currentSize + 5;
        int y = catPos.y + 5;
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        if (x + tooltipWindow.getWidth() > screen.width) x = catPos.x - tooltipWindow.getWidth() - 5;
        if (y + tooltipWindow.getHeight() > screen.height) y = catPos.y - tooltipWindow.getHeight() - 5;
        tooltipWindow.setLocation(x, y);
    }

    private static void showSelfMemory() {
        JOptionPane.showMessageDialog(window, String.format("📊 程序自身内存占用\n\n📌 当前 JVM 堆内存: %.2f MB\n\n💡 正常范围：10-100 MB", currentSelfMemory), "自身内存", JOptionPane.INFORMATION_MESSAGE);
    }

    private static void showStatistics() {
        if (cpuHistory.isEmpty()) {
            JOptionPane.showMessageDialog(window, "暂无数据，请稍后再试。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        double avgCpu = 0, maxCpu = 0, avgMem = 0, maxMem = 0;
        for (double c : cpuHistory) { avgCpu += c; if (c > maxCpu) maxCpu = c; }
        for (double m : memoryHistory) { avgMem += m; if (m > maxMem) maxMem = m; }
        avgCpu /= cpuHistory.size();
        avgMem /= memoryHistory.size();
        String msg = String.format("📊 今日统计\n\n💻 CPU 平均: %.1f%%  最高: %.1f%%\n🧠 内存平均: %.1f%%  最高: %.1f%%\n📌 自身内存: %.2f MB\n🔔 告警: CPU>%.0f%% MEM>%.0f%%\n📈 记录: %d 条",
                avgCpu, maxCpu, avgMem, maxMem, currentSelfMemory, cpuWarningThreshold, memoryWarningThreshold, cpuHistory.size());
        JOptionPane.showMessageDialog(window, msg, "系统统计", JOptionPane.INFORMATION_MESSAGE);
    }

    private static void setWindowPosition() {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        window.setLocation(bounds.x + bounds.width - currentSize - 10, bounds.y + bounds.height - currentSize - 10);
    }

    private static ImageIcon createPlaceholder(int frame, String pet, int size) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color color = pet.equals("dog") ? new Color(100, 150, 255) : Color.ORANGE;
        g.setColor(color);
        g.fillRect(0, 0, size, size);
        g.setColor(Color.BLACK);
        int eye = size / 8;
        g.fillOval(size / 4, size / 3, eye, eye);
        g.fillOval(size * 3 / 4 - eye, size / 3, eye, eye);
        g.setStroke(new BasicStroke(size / 32f));
        if (frame == 0 || frame == 2) {
            g.drawArc(size / 2 - size / 8, size * 2 / 3, size / 4, size / 10, 0, 180);
        } else {
            g.fillOval(size / 2 - size / 6, size * 2 / 3, size / 3, size / 16);
        }
        g.dispose();
        return new ImageIcon(img);
    }

    private static void setupMouseEvents() {
        catLabel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    popupMenu.show(catLabel, e.getX(), e.getY());
                }
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    showInfoPanel();
                }
            }
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragStartPoint = e.getPoint();
                    isDragging = true;
                }
            }
            public void mouseReleased(MouseEvent e) {
                isDragging = false;
                dragStartPoint = null;
            }
        });
        catLabel.addMouseMotionListener(new MouseAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (isDragging && dragStartPoint != null) {
                    Point loc = window.getLocation();
                    window.setLocation(loc.x + e.getX() - dragStartPoint.x, loc.y + e.getY() - dragStartPoint.y);
                }
            }
        });
    }
}