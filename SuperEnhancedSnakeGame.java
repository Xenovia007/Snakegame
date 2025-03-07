

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import javax.sound.sampled.*;

public class SuperEnhancedSnakeGame extends JPanel implements ActionListener, KeyListener {
    private final int TILE_SIZE = 25;
    private final int WIDTH = 800;
    private final int HEIGHT = 600;
    private final int ALL_TILES = (WIDTH * HEIGHT) / (TILE_SIZE * TILE_SIZE);
    private ArrayList<Point> snake = new ArrayList<>();
    private Point food, powerUp;
    private char direction = 'R';
    private boolean running = true;
    private boolean paused = false;
    private Timer timer;
    private int score = 0;
    private int speed = 100;
    private Random random = new Random();
    private ArrayList<Point> obstacles = new ArrayList<>();
    private String currentBackground = "Day";  // Day or Night

    private Clip eatingClip;
    private Clip gameOverClip;

    public SuperEnhancedSnakeGame() {
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setBackground(Color.BLACK);
        this.setFocusable(true);
        this.addKeyListener(this);
        startGame();
    }


    public void startGame() {
        snake.clear();
        snake.add(new Point(WIDTH / 2, HEIGHT / 2));
        direction = 'R';
        score = 0;
        speed = 100;
        spawnFood();
        spawnPowerUp();
        spawnObstacles();
        if (timer != null) {
            timer.stop();
        }
        timer = new Timer(speed, this);
        timer.start();
        running = true;
        paused = false;
    }

    public void spawnFood() {
        Point newFood;
        do {
            int x = random.nextInt(WIDTH / TILE_SIZE) * TILE_SIZE;
            int y = random.nextInt(HEIGHT / TILE_SIZE) * TILE_SIZE;
            newFood = new Point(x, y);
        } while (snake.contains(newFood) || obstacles.contains(newFood));
        food = newFood;
    }

    public void spawnPowerUp() {
        Point newPowerUp;
        do {
            int x = random.nextInt(WIDTH / TILE_SIZE) * TILE_SIZE;
            int y = random.nextInt(HEIGHT / TILE_SIZE) * TILE_SIZE;
            newPowerUp = new Point(x, y);
        } while (snake.contains(newPowerUp) || obstacles.contains(newPowerUp));
        powerUp = newPowerUp;
    }

    public void spawnObstacles() {
        obstacles.clear();
        int numObstacles = random.nextInt(5) + 5; // Random between 5 to 10 obstacles
        for (int i = 0; i < numObstacles; i++) {
            int x = random.nextInt(WIDTH / TILE_SIZE) * TILE_SIZE;
            int y = random.nextInt(HEIGHT / TILE_SIZE) * TILE_SIZE;
            obstacles.add(new Point(x, y));
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Set background color to black
        this.setBackground(Color.BLACK);

        if (running) {
            // Draw food
            g.setColor(Color.RED); // Normal food
            g.fillRect(food.x, food.y, TILE_SIZE, TILE_SIZE);

            // Draw power-up
            g.setColor(Color.CYAN); // Power-up
            g.fillRect(powerUp.x, powerUp.y, TILE_SIZE, TILE_SIZE);

            // Draw obstacles
            g.setColor(Color.GRAY);
            for (Point obstacle : obstacles) {
                g.fillRect(obstacle.x, obstacle.y, TILE_SIZE, TILE_SIZE);
            }

            // Draw snake
            for (int i = 0; i < snake.size(); i++) {
                if (i == 0) {
                    g.setColor(Color.YELLOW); // Snake head
                } else {
                    g.setColor(Color.GREEN); // Snake body
                }
                g.fillRect(snake.get(i).x, snake.get(i).y, TILE_SIZE, TILE_SIZE);
            }

            // Draw score
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.drawString("Score: " + score, 10, 20);
        } else {
            // Game Over screen
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            g.drawString("Game Over!", WIDTH / 2 - 60, HEIGHT / 2 - 20);
            g.drawString("Score: " + score, WIDTH / 2 - 40, HEIGHT / 2);
            g.drawString("Press R to Restart", WIDTH / 2 - 80, HEIGHT / 2 + 30);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (running && !paused) {
            move();
            checkCollision();
            checkFood();
            checkPowerUp();
        }
        repaint();
    }

    public void move() {
        Point head = snake.get(0);
        Point newHead = new Point(head);

        switch (direction) {
            case 'R': newHead.x += TILE_SIZE; break;
            case 'L': newHead.x -= TILE_SIZE; break;
            case 'U': newHead.y -= TILE_SIZE; break;
            case 'D': newHead.y += TILE_SIZE; break;
        }

        snake.add(0, newHead);
        if (!newHead.equals(food) && !newHead.equals(powerUp)) {
            snake.remove(snake.size() - 1); // Remove tail
        }
    }

    public void checkCollision() {
        Point head = snake.get(0);

        // Check wall collision
        if (head.x < 0 || head.x >= WIDTH || head.y < 0 || head.y >= HEIGHT) {
            running = false;
            playGameOverSound();
        }

        // Check self-collision
        for (int i = 1; i < snake.size(); i++) {
            if (head.equals(snake.get(i))) {
                running = false;
                playGameOverSound();
            }
        }

        // Check obstacle collision
        if (obstacles.contains(head)) {
            running = false;
            playGameOverSound();
        }
    }

    public void checkFood() {
        if (snake.get(0).equals(food)) {
            snake.add(new Point(food)); // Add length
            score += 10;
            spawnFood();
            playEatingSound();
        }
    }

    public void checkPowerUp() {
        if (snake.get(0).equals(powerUp)) {
            score += 20;
            spawnPowerUp();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP: if (direction != 'D') direction = 'U'; break;
            case KeyEvent.VK_DOWN: if (direction != 'U') direction = 'D'; break;
            case KeyEvent.VK_LEFT: if (direction != 'R') direction = 'L'; break;
            case KeyEvent.VK_RIGHT: if (direction != 'L') direction = 'R'; break;
            case KeyEvent.VK_R: if (!running) startGame(); break;
            case KeyEvent.VK_P: paused = !paused; break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void keyTyped(KeyEvent e) {}

    public void playEatingSound() {
        if (eatingClip != null) {
            eatingClip.setFramePosition(0);
            eatingClip.start();
        }
    }

    public void playGameOverSound() {
        if (gameOverClip != null) {
            gameOverClip.setFramePosition(0);
            gameOverClip.start();
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Super Enhanced Snake Game");
        SuperEnhancedSnakeGame game = new SuperEnhancedSnakeGame();

        frame.add(game);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }
}
