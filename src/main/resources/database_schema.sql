USE campusfit;

-- Table to store weekly timetable classes (adaptive module)
CREATE TABLE IF NOT EXISTS timetable_class (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    subject_name VARCHAR(255) NOT NULL,
    day_of_week VARCHAR(20) NOT NULL, -- e.g., 'MONDAY', 'TUESDAY'
    start_time TIME NOT NULL,
    end_time TIME NOT NULL
);

-- Table to store academic schedule (AcademicSchedule entity)
CREATE TABLE IF NOT EXISTS academic_schedule (
    schedule_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    subject_name VARCHAR(255) NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL
);

-- Table to store academic tasks (AcademicTask entity: Assignments, Exams, Projects, Labs)
CREATE TABLE IF NOT EXISTS academic_task (
    task_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    task_type VARCHAR(50) NOT NULL, -- 'ASSIGNMENT', 'EXAM', 'PROJECT', 'LAB'
    deadline DATETIME NOT NULL,
    estimated_hours DOUBLE DEFAULT 1.0,
    priority VARCHAR(50) DEFAULT 'MEDIUM', -- 'HIGH', 'MEDIUM', 'LOW'
    status VARCHAR(50) DEFAULT 'PENDING' -- 'PENDING', 'COMPLETED'
);

-- Table to store generated study plan sessions (StudyPlan entity)
CREATE TABLE IF NOT EXISTS study_plan (
    plan_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING', -- 'PENDING', 'COMPLETED'
    explanation TEXT
);
-- If study_plan already exists without explanation:
-- ALTER TABLE study_plan ADD COLUMN explanation TEXT;


-- Table to store intelligent workout plans (WorkoutPlan entity)
CREATE TABLE IF NOT EXISTS workout_plan (
    workout_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    workout_date DATE NOT NULL,
    workout_title VARCHAR(255) NOT NULL,
    workout_goal VARCHAR(100) NOT NULL,
    workout_focus VARCHAR(100) NOT NULL,
    fitness_level VARCHAR(50) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    duration INT NOT NULL, -- minutes
    calories_estimated INT NOT NULL,
    workout_details LONGTEXT,
    adjustment_reason VARCHAR(255),
    status VARCHAR(50) DEFAULT 'PENDING' -- 'PENDING', 'COMPLETED'
);

-- Update existing schedule_item table to hold the final generated adaptive plan
-- If it exists, we drop or alter. Let's assume we drop and recreate for clean slate.
DROP TABLE IF EXISTS schedule_item;
CREATE TABLE schedule_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    task_type VARCHAR(50) NOT NULL, -- 'CLASS', 'STUDY', 'WORKOUT', 'BREAK', 'MEAL', 'SLEEP'
    description TEXT,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING'
);

-- Table to store user feedback
CREATE TABLE student_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    mood VARCHAR(50),
    academic_load_score INT,
    workout_difficulty_score INT,
    planner_satisfaction_score INT,
    missed_activity_reason TEXT,
    improvement_suggestions TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Table to store Intelligent Workout Plans
CREATE TABLE IF NOT EXISTS workout_plan (
    workout_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    workout_date DATE NOT NULL,
    workout_title VARCHAR(255) NOT NULL,
    workout_goal VARCHAR(100) NOT NULL,
    workout_focus VARCHAR(100) NOT NULL,
    fitness_level VARCHAR(50) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    duration INT NOT NULL,
    calories_estimated INT NOT NULL,
    workout_details LONGTEXT,
    adjustment_reason VARCHAR(255),
    status VARCHAR(50) DEFAULT 'PENDING'
);

-- Table to store BMI Records & History
CREATE TABLE IF NOT EXISTS bmi_record (
    record_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    height DOUBLE NOT NULL,
    weight DOUBLE NOT NULL,
    bmi DOUBLE NOT NULL,
    category VARCHAR(100) NOT NULL,
    health_score INT NOT NULL,
    recorded_at DATETIME DEFAULT CURRENT_TIMESTAMP
);


