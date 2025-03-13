import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import javax.sound.sampled.*;
import java.io.*;

public class SuperEnhancedSnakeGame extends JPanel implements ActionListener, KeyListener {
    // Constants and game dimensions
    private final int TILE_SIZE = 25;
    private final int WIDTH = 800;
    private final int HEIGHT = 600;
    
    // Game objects
    private ArrayList<Point> snake = new ArrayList<>();
    private ArrayList<Point> enemySnake = new ArrayList<>();
    private Point food, powerUp;
    private ArrayList<Point> obstacles = new ArrayList<>();
    
    // Directions
    private char direction = 'R';
    private char enemyDirection = 'L';
    
    // Game states
    private boolean running = true;
    private boolean paused = false;
    private Timer timer;
    private int speed = 100; // Timer delay (ms)
    private int elapsedTime = 0; // For UI (ms)
    
    // Scores and high score (leaderboard)
    private int playerScore = 0;
    private int enemyScore = 0;
    private int highScore = 0;
    
    // Pass-through (obstacle bypass) power-up state for player and enemy
    private boolean playerPassThroughActive = false;
    private int playerPassThroughTicks = 0; // ticks remaining for effect
    private boolean enemyPassThroughActive = false;
    private int enemyPassThroughTicks = 0;
    
    // Random generator
    private Random random = new Random();
    
    // Audio clips
    private Clip eatingClip;
    private Clip gameOverClip;
    private Clip powerUpClip;
    
    // Leaderboard file
    private final String HIGH_SCORE_FILE = "highscore.txt";

    public SuperEnhancedSnakeGame() {
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setBackground(Color.BLACK);
        this.setFocusable(true);
        this.addKeyListener(this);
        loadSounds();
        loadHighScore();
        startGame();
    }
    
    // Load sound effects
    public void loadSounds() {
        try {
            AudioInputStream eatingSound = AudioSystem.getAudioInputStream(getClass().getResource("/sounds/Pop sound effect.mp3"));
            eatingClip = AudioSystem.getClip();
            eatingClip.open(eatingSound);
            
            AudioInputStream gameOverSound = AudioSystem.getAudioInputStream(getClass().getResource("/sounds/gameOver.wav"));
            gameOverClip = AudioSystem.getClip();
            gameOverClip.open(gameOverSound);
            
            AudioInputStream powerUpSound = AudioSystem.getAudioInputStream(getClass().getResource("/sounds/powerUp.wav"));
            powerUpClip = AudioSystem.getClip();
            powerUpClip.open(powerUpSound);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Load high score from file
    public void loadHighScore() {
        try (BufferedReader br = new BufferedReader(new FileReader(HIGH_SCORE_FILE))) {
            String line = br.readLine();
            if (line != null) {
                highScore = Integer.parseInt(line.trim());
            }
        } catch (IOException | NumberFormatException e) {
            highScore = 0;
        }
    }
    
    // Save high score to file
    public void saveHighScore() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(HIGH_SCORE_FILE))) {
            pw.println(highScore);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // Start or restart the game
    public void startGame() {
        snake.clear();
        enemySnake.clear();
        obstacles.clear();
        playerScore = 0;
        enemyScore = 0;
        elapsedTime = 0;
        playerPassThroughActive = false;
        playerPassThroughTicks = 0;
        enemyPassThroughActive = false;
        enemyPassThroughTicks = 0;
        
        // Initialize positions
        snake.add(new Point(WIDTH / 2, HEIGHT / 2));
        enemySnake.add(new Point(random.nextInt(WIDTH / TILE_SIZE) * TILE_SIZE,
                                   random.nextInt(HEIGHT / TILE_SIZE) * TILE_SIZE));
                                   
        direction = 'R';
        enemyDirection = 'L';
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
    
    // Spawn food in a free location
    public void spawnFood() {
        Point newFood;
        do {
            int x = random.nextInt(WIDTH / TILE_SIZE) * TILE_SIZE;
            int y = random.nextInt(HEIGHT / TILE_SIZE) * TILE_SIZE;
            newFood = new Point(x, y);
        } while (snake.contains(newFood) || enemySnake.contains(newFood) || obstacles.contains(newFood));
        food = newFood;
    }
    
    // Spawn power-up (blue power-up grants pass-through ability without changing speed)
    public void spawnPowerUp() {
        Point newPowerUp;
        do {
            int x = random.nextInt(WIDTH / TILE_SIZE) * TILE_SIZE;
            int y = random.nextInt(HEIGHT / TILE_SIZE) * TILE_SIZE;
            newPowerUp = new Point(x, y);
        } while (snake.contains(newPowerUp) || enemySnake.contains(newPowerUp) || obstacles.contains(newPowerUp));
        powerUp = newPowerUp;
    }
    
    // Spawn obstacles ensuring they don't block initial snake positions
    public void spawnObstacles() {
        obstacles.clear();
        int numObstacles = random.nextInt(5) + 5;
        for (int i = 0; i < numObstacles; i++) {
            int x = random.nextInt(WIDTH / TILE_SIZE) * TILE_SIZE;
            int y = random.nextInt(HEIGHT / TILE_SIZE) * TILE_SIZE;
            Point p = new Point(x, y);
            if (!snake.contains(p) && !enemySnake.contains(p))
                obstacles.add(p);
            else
                i--;
        }
    }
    
    // Respawn enemy snake in a free location
    public void respawnEnemy() {
        enemySnake.clear();
        Point newEnemy;
        do {
            int x = random.nextInt(WIDTH / TILE_SIZE) * TILE_SIZE;
            int y = random.nextInt(HEIGHT / TILE_SIZE) * TILE_SIZE;
            newEnemy = new Point(x, y);
        } while (snake.contains(newEnemy) || obstacles.contains(newEnemy));
        enemySnake.add(newEnemy);
        enemyDirection = new char[] { 'R', 'L', 'U', 'D' }[random.nextInt(4)];
    }
    
    // Get enemy target:
    // In early game (enemyScore < 100), enemy focuses on food/power-ups.
    // After 100 points, enemy chases the player's head.
    private Point getEnemyTarget() {
        if (enemyScore < 100) {
            Point enemyHead = enemySnake.get(0);
            return (enemyHead.distance(food) <= enemyHead.distance(powerUp)) ? food : powerUp;
        }
        return snake.get(0);
    }
    
    // Update enemy direction based on target and safety conditions
    private void updateEnemyDirection() {
        if (enemySnake.isEmpty() || snake.isEmpty()) return;
        Point enemyHead = enemySnake.get(0);
        Point target = getEnemyTarget();
        ArrayList<Character> possibleMoves = new ArrayList<>();
        possibleMoves.add('R'); possibleMoves.add('L'); possibleMoves.add('U'); possibleMoves.add('D');
        
        ArrayList<Character> safeMoves = new ArrayList<>();
        for (char move : possibleMoves) {
            Point newPos = new Point(enemyHead);
            switch(move) {
                case 'R': newPos.x += TILE_SIZE; break;
                case 'L': newPos.x -= TILE_SIZE; break;
                case 'U': newPos.y -= TILE_SIZE; break;
                case 'D': newPos.y += TILE_SIZE; break;
            }
            if (newPos.x >= 0 && newPos.x < WIDTH && newPos.y >= 0 && newPos.y < HEIGHT &&
                !obstacles.contains(newPos) && !enemySnake.contains(newPos))
                safeMoves.add(move);
        }
        if (!safeMoves.isEmpty()) {
            char bestMove = safeMoves.get(0);
            double bestDistance = Double.MAX_VALUE;
            for (char move : safeMoves) {
                Point newPos = new Point(enemyHead);
                switch(move) {
                    case 'R': newPos.x += TILE_SIZE; break;
                    case 'L': newPos.x -= TILE_SIZE; break;
                    case 'U': newPos.y -= TILE_SIZE; break;
                    case 'D': newPos.y += TILE_SIZE; break;
                }
                double distance = newPos.distance(target);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestMove = move;
                }
            }
            enemyDirection = bestMove;
        } else {
            char[] directions = {'R', 'L', 'U', 'D'};
            enemyDirection = directions[random.nextInt(4)];
        }
    }
    
    // Render game elements and UI
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        this.setBackground(Color.BLACK);
        
        if (running) {
            // Draw food and power-up
            g.setColor(Color.RED);
            g.fillRect(food.x, food.y, TILE_SIZE, TILE_SIZE);
            g.setColor(Color.CYAN); // Blue power-up for pass-through effect
            g.fillRect(powerUp.x, powerUp.y, TILE_SIZE, TILE_SIZE);
            
            // Draw obstacles
            g.setColor(Color.GRAY);
            for (Point obstacle : obstacles) {
                g.fillRect(obstacle.x, obstacle.y, TILE_SIZE, TILE_SIZE);
            }
            
            // Draw player snake
            for (int i = 0; i < snake.size(); i++) {
                g.setColor(i == 0 ? Color.YELLOW : Color.GREEN);
                g.fillRect(snake.get(i).x, snake.get(i).y, TILE_SIZE, TILE_SIZE);
            }
            
            // Draw enemy snake
            for (int i = 0; i < enemySnake.size(); i++) {
                g.setColor(i == 0 ? Color.ORANGE : Color.RED);
                g.fillRect(enemySnake.get(i).x, enemySnake.get(i).y, TILE_SIZE, TILE_SIZE);
            }
            
            // Draw UI: scores, high score, elapsed time, power-up indicators
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.drawString("Player: " + playerScore, 10, 20);
            g.drawString("Enemy: " + enemyScore, WIDTH - 150, 20);
            g.drawString("High Score: " + highScore, 10, 40);
            g.drawString("Time: " + (elapsedTime / 1000) + "s", WIDTH / 2 - 30, 20);
            if (playerPassThroughActive) {
                g.drawString("Player Pass-Through!", WIDTH / 2 - 70, 40);
            }
            if (enemyPassThroughActive) {
                g.drawString("Enemy Pass-Through!", WIDTH / 2 - 70, 60);
            }
        } else {
            // Game Over screen
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            g.drawString("Game Over!", WIDTH / 2 - 60, HEIGHT / 2 - 20);
            g.drawString("Player: " + playerScore, WIDTH / 2 - 50, HEIGHT / 2);
            g.drawString("Enemy: " + enemyScore, WIDTH / 2 - 50, HEIGHT / 2 + 30);
            g.drawString("High Score: " + highScore, WIDTH / 2 - 50, HEIGHT / 2 + 60);
            g.drawString("Press R to Restart", WIDTH / 2 - 80, HEIGHT / 2 + 90);
        }
    }
    
    // Main game loop: update state, process power-up effects, and increment timer
    @Override
    public void actionPerformed(ActionEvent e) {
        if (running && !paused) {
            move();
            updateEnemyDirection();
            moveEnemy();
            checkCollision();
            checkFood();
            checkPowerUp();
            
            // Update pass-through timers for player and enemy
            if (playerPassThroughActive) {
                playerPassThroughTicks--;
                if (playerPassThroughTicks <= 0) {
                    playerPassThroughActive = false;
                }
            }
            if (enemyPassThroughActive) {
                enemyPassThroughTicks--;
                if (enemyPassThroughTicks <= 0) {
                    enemyPassThroughActive = false;
                }
            }
            
            elapsedTime += timer.getDelay();
        }
        repaint();
    }
    
    // Move player's snake
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
            snake.remove(snake.size() - 1);
        }
    }
    
    // Move enemy snake
    public void moveEnemy() {
        if (enemySnake.isEmpty()) {
            respawnEnemy();
            return;
        }
        Point head = enemySnake.get(0);
        Point newHead = new Point(head);
        switch (enemyDirection) {
            case 'R': newHead.x += TILE_SIZE; break;
            case 'L': newHead.x -= TILE_SIZE; break;
            case 'U': newHead.y -= TILE_SIZE; break;
            case 'D': newHead.y += TILE_SIZE; break;
        }
        enemySnake.add(0, newHead);
        if (!newHead.equals(food) && !newHead.equals(powerUp)) {
            enemySnake.remove(enemySnake.size() - 1);
        }
    }
    
    // Check collisions and update scores/state
    public void checkCollision() {
        Point head = snake.get(0);
        Point enemyHead = enemySnake.get(0);
        
        // Player collisions with walls or self always trigger game over.
        // Ignore obstacles collision if pass-through is active.
        if (head.x < 0 || head.x >= WIDTH || head.y < 0 || head.y >= HEIGHT ||
            (!playerPassThroughActive && obstacles.contains(head)) ||
            (snake.size() > 1 && snake.subList(1, snake.size()).contains(head))) {
            running = false;
            playGameOverSound();
            updateHighScore();
        }
        
        // Enemy collisions: if enemy pass-through active, ignore obstacles.
        if (enemyHead.x < 0 || enemyHead.x >= WIDTH || enemyHead.y < 0 || enemyHead.y >= HEIGHT ||
            (!enemyPassThroughActive && obstacles.contains(enemyHead)) ||
            (enemySnake.size() > 1 && enemySnake.subList(1, enemySnake.size()).contains(enemyHead))) {
            enemyScore -= 50;
            respawnEnemy();
        }
        
        // Collisions between snakes
        if (enemySnake.size() > 1 && enemySnake.subList(1, enemySnake.size()).contains(head)) {
            running = false;
            playGameOverSound();
            updateHighScore();
        }
        if (snake.size() > 1 && snake.subList(1, snake.size()).contains(enemyHead)) {
            enemyScore -= 50;
            respawnEnemy();
        }
        if (head.equals(enemyHead)) {
            running = false;
            playGameOverSound();
            updateHighScore();
        }
    }
    
    // Check if food is eaten and update scores
    public void checkFood() {
        if (snake.get(0).equals(food)) {
            snake.add(new Point(food));
            playerScore += 10;
            spawnFood();
            playEatingSound();
        }
        if (enemySnake.get(0).equals(food)) {
            enemySnake.add(new Point(food));
            enemyScore += 10;
            spawnFood();
            playEatingSound();
        }
    }
    
    // Check if power-up is eaten and trigger pass-through effect without increasing speed
    public void checkPowerUp() {
        if (snake.get(0).equals(powerUp)) {
            playerScore += 20;
            playerPassThroughActive = true;
            playerPassThroughTicks = 50; // effect lasts 50 ticks
            spawnPowerUp();
            playPowerUpSound();
        }
        if (enemySnake.get(0).equals(powerUp)) {
            enemySnake.add(new Point(powerUp));
            enemyScore += 20;
            enemyPassThroughActive = true;
            enemyPassThroughTicks = 50;
            spawnPowerUp();
            playPowerUpSound();
        }
    }
    
    // Sound effect methods
    public void playEatingSound() {
        if (eatingClip != null) {
            eatingClip.setFramePosition(0);
            eatingClip.start();
        }
    }
    
    public void playPowerUpSound() {
        if (powerUpClip != null) {
            powerUpClip.setFramePosition(0);
            powerUpClip.start();
        }
    }
    
    public void playGameOverSound() {
        if (gameOverClip != null) {
            gameOverClip.setFramePosition(0);
            gameOverClip.start();
        }
    }
    
    // Update high score if player's score exceeds it
    public void updateHighScore() {
        if (playerScore > highScore) {
            highScore = playerScore;
            saveHighScore();
        }
    }
    
    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:    if (direction != 'D') direction = 'U'; break;
            case KeyEvent.VK_DOWN:  if (direction != 'U') direction = 'D'; break;
            case KeyEvent.VK_LEFT:  if (direction != 'R') direction = 'L'; break;
            case KeyEvent.VK_RIGHT: if (direction != 'L') direction = 'R'; break;
            case KeyEvent.VK_R:     if (!running) startGame(); break;
            case KeyEvent.VK_P:     paused = !paused; break;
        }
    }
    
    @Override
    public void keyReleased(KeyEvent e) { }
    
    @Override
    public void keyTyped(KeyEvent e) { }
    
    public static void main(String[] args) {
        JFrame frame = new JFrame("SuperEnhancedSnake");
        SuperEnhancedSnakeGame game = new SuperEnhancedSnakeGame();
        frame.add(game);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }
}
