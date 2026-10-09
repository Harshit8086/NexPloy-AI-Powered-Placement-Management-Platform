-- Seed the aptitude question bank (V53). ~60 curated, self-contained MCQs across four categories:
-- QUANTITATIVE, LOGICAL, VERBAL, DATA_INTERPRETATION. All are global (campus_id NULL) so every campus
-- can draw from them; officers may add campus-scoped questions later (CRUD is a follow-up). Correct
-- options are spread across A-D on purpose so the answer position carries no signal. This runs on
-- Postgres only (the H2 test profile has Flyway disabled and derives no seed data).

INSERT INTO aptitude_questions
    (id, campus_id, category, difficulty, prompt, option_a, option_b, option_c, option_d, correct_option, explanation, active, created_at, updated_at)
VALUES
    ('apq_seed_q001', NULL, 'QUANTITATIVE', 'easy', 'What is 15% of 200?', '25', '30', '35', '40', 'B', '0.15 x 200 = 30.', true, now(), now()),
    ('apq_seed_q002', NULL, 'QUANTITATIVE', 'easy', 'What is the average of 10, 20, 30, 40 and 50?', '30', '25', '35', '40', 'A', 'Sum 150 divided by 5 = 30.', true, now(), now()),
    ('apq_seed_q003', NULL, 'QUANTITATIVE', 'easy', 'If 5 pens cost 75 rupees, what do 8 pens cost (in rupees)?', '100', '110', '120', '130', 'C', 'One pen costs 15; 8 x 15 = 120.', true, now(), now()),
    ('apq_seed_q004', NULL, 'QUANTITATIVE', 'medium', 'A train at 60 km/h crosses a pole in 30 seconds. What is its length (in metres)?', '450', '600', '550', '500', 'D', '60 km/h = 16.67 m/s; 16.67 x 30 = 500 m.', true, now(), now()),
    ('apq_seed_q005', NULL, 'QUANTITATIVE', 'medium', 'A finishes a job in 10 days and B in 15 days. Working together they finish in how many days?', '5', '6', '7', '8', 'B', '1/10 + 1/15 = 1/6, so 6 days.', true, now(), now()),
    ('apq_seed_q006', NULL, 'QUANTITATIVE', 'medium', 'Compound interest on 1000 rupees at 10% per annum for 2 years (in rupees)?', '200', '220', '210', '110', 'C', '1000 x 1.1 x 1.1 = 1210; interest = 210.', true, now(), now()),
    ('apq_seed_q007', NULL, 'QUANTITATIVE', 'easy', 'Two numbers are in the ratio 3:5 and add up to 40. What is the larger number?', '25', '15', '20', '24', 'A', '40 divided by 8 parts = 5; 5 x 5 = 25.', true, now(), now()),
    ('apq_seed_q008', NULL, 'QUANTITATIVE', 'easy', 'What is 25% of 25% of 800?', '100', '25', '200', '50', 'D', '0.25 x 0.25 x 800 = 50.', true, now(), now()),
    ('apq_seed_q009', NULL, 'QUANTITATIVE', 'medium', 'A number increased by 20% becomes 60. What is the number?', '48', '50', '45', '52', 'B', '60 divided by 1.2 = 50.', true, now(), now()),
    ('apq_seed_q010', NULL, 'QUANTITATIVE', 'easy', 'Convert 90 km/h to m/s.', '20', '30', '25', '15', 'C', 'Multiply by 5/18: 90 x 5/18 = 25.', true, now(), now()),
    ('apq_seed_q011', NULL, 'QUANTITATIVE', 'medium', 'A man rows at 5 km/h in still water and the stream flows at 1 km/h. His downstream speed (km/h)?', '4', '5', '7', '6', 'D', 'Downstream = 5 + 1 = 6 km/h.', true, now(), now()),
    ('apq_seed_q012', NULL, 'QUANTITATIVE', 'easy', 'Cost price is 400 and selling price is 500. What is the profit percent?', '25', '20', '10', '15', 'A', 'Profit 100 on 400 = 25%.', true, now(), now()),
    ('apq_seed_q013', NULL, 'QUANTITATIVE', 'medium', 'If x + 1/x = 2, then x equals?', '0', '2', '1', '-1', 'C', 'Only x = 1 satisfies x + 1/x = 2.', true, now(), now()),
    ('apq_seed_q014', NULL, 'QUANTITATIVE', 'easy', 'What is the LCM of 4 and 6?', '24', '12', '6', '10', 'B', 'Smallest common multiple of 4 and 6 is 12.', true, now(), now()),
    ('apq_seed_q015', NULL, 'QUANTITATIVE', 'easy', 'What is the HCF of 12 and 18?', '3', '9', '12', '6', 'D', 'Greatest common factor of 12 and 18 is 6.', true, now(), now());

INSERT INTO aptitude_questions
    (id, campus_id, category, difficulty, prompt, option_a, option_b, option_c, option_d, correct_option, explanation, active, created_at, updated_at)
VALUES
    ('apq_seed_q016', NULL, 'LOGICAL', 'easy', 'Find the next number in the series: 2, 4, 8, 16, ?', '24', '32', '30', '20', 'B', 'Each term doubles; 16 x 2 = 32.', true, now(), now()),
    ('apq_seed_q017', NULL, 'LOGICAL', 'easy', 'Find the next number in the series: 1, 4, 9, 16, ?', '20', '24', '25', '36', 'C', 'Perfect squares; 5 squared = 25.', true, now(), now()),
    ('apq_seed_q018', NULL, 'LOGICAL', 'easy', 'Find the odd one out: 3, 5, 9, 11', '9', '3', '5', '11', 'A', '9 is not a prime number; the rest are.', true, now(), now()),
    ('apq_seed_q019', NULL, 'LOGICAL', 'easy', 'If A=1, B=2, C=3 and so on, what is the sum of the letters in CAB?', '5', '7', '9', '6', 'D', 'C + A + B = 3 + 1 + 2 = 6.', true, now(), now()),
    ('apq_seed_q020', NULL, 'LOGICAL', 'easy', 'Find the next number in the series: 5, 10, 20, 40, ?', '60', '80', '70', '100', 'B', 'Each term doubles; 40 x 2 = 80.', true, now(), now()),
    ('apq_seed_q021', NULL, 'LOGICAL', 'medium', 'Find the next number in the series: 3, 6, 11, 18, ?', '25', '29', '27', '24', 'C', 'Differences are 3, 5, 7, 9; 18 + 9 = 27.', true, now(), now()),
    ('apq_seed_q022', NULL, 'LOGICAL', 'easy', 'Bird is to Nest as Bee is to ?', 'Hive', 'Web', 'Hole', 'Den', 'A', 'A bee lives in a hive, as a bird lives in a nest.', true, now(), now()),
    ('apq_seed_q023', NULL, 'LOGICAL', 'medium', 'Find the next number in the series: 100, 95, 85, 70, ?', '55', '45', '60', '50', 'D', 'Differences are 5, 10, 15, 20; 70 - 20 = 50.', true, now(), now()),
    ('apq_seed_q024', NULL, 'LOGICAL', 'easy', 'Find the odd one out: Dog, Cat, Rose, Lion', 'Dog', 'Rose', 'Lion', 'Cat', 'B', 'Rose is a flower; the rest are animals.', true, now(), now()),
    ('apq_seed_q025', NULL, 'LOGICAL', 'easy', 'Facing North, you turn right twice. Which direction do you face?', 'East', 'West', 'South', 'North', 'C', 'Two right turns of 90 degrees reverse the direction to South.', true, now(), now()),
    ('apq_seed_q026', NULL, 'LOGICAL', 'easy', 'Find the next letter in the series: A, C, E, G, ?', 'H', 'I', 'J', 'K', 'B', 'Skip one letter each time; after G comes I.', true, now(), now()),
    ('apq_seed_q027', NULL, 'LOGICAL', 'easy', 'Find the next number in the series: 2, 3, 5, 7, 11, ?', '13', '12', '15', '14', 'A', 'These are consecutive primes; after 11 comes 13.', true, now(), now()),
    ('apq_seed_q028', NULL, 'LOGICAL', 'easy', 'If today is Monday, what day is it 3 days later?', 'Wednesday', 'Friday', 'Tuesday', 'Thursday', 'D', 'Monday + 3 days = Thursday.', true, now(), now()),
    ('apq_seed_q029', NULL, 'LOGICAL', 'medium', 'Find the next number in the Fibonacci series: 1, 1, 2, 3, 5, 8, ?', '11', '13', '12', '15', 'B', 'Each term is the sum of the previous two; 5 + 8 = 13.', true, now(), now()),
    ('apq_seed_q030', NULL, 'LOGICAL', 'medium', 'Find the odd one out: 8, 27, 64, 100', '8', '27', '100', '64', 'C', '8, 27 and 64 are perfect cubes; 100 is not.', true, now(), now());

INSERT INTO aptitude_questions
    (id, campus_id, category, difficulty, prompt, option_a, option_b, option_c, option_d, correct_option, explanation, active, created_at, updated_at)
VALUES
    ('apq_seed_q031', NULL, 'VERBAL', 'easy', 'Choose the synonym of HAPPY.', 'Sad', 'Joyful', 'Angry', 'Tired', 'B', 'Joyful means feeling or showing happiness.', true, now(), now()),
    ('apq_seed_q032', NULL, 'VERBAL', 'easy', 'Choose the antonym of ANCIENT.', 'Old', 'Historic', 'Modern', 'Aged', 'C', 'Modern is the opposite of ancient.', true, now(), now()),
    ('apq_seed_q033', NULL, 'VERBAL', 'easy', 'Choose the synonym of RAPID.', 'Fast', 'Slow', 'Steady', 'Late', 'A', 'Rapid and fast both mean quick.', true, now(), now()),
    ('apq_seed_q034', NULL, 'VERBAL', 'easy', 'Choose the antonym of EXPAND.', 'Grow', 'Widen', 'Stretch', 'Contract', 'D', 'Contract means to become smaller, the opposite of expand.', true, now(), now()),
    ('apq_seed_q035', NULL, 'VERBAL', 'easy', 'Fill in the blank: She is good ___ mathematics.', 'in', 'at', 'on', 'for', 'B', 'The idiom is good at a subject or skill.', true, now(), now()),
    ('apq_seed_q036', NULL, 'VERBAL', 'medium', 'Fill in the blank: He has been living here ___ 2010.', 'for', 'from', 'since', 'at', 'C', 'Since is used with a point in time such as a year.', true, now(), now()),
    ('apq_seed_q037', NULL, 'VERBAL', 'easy', 'Choose the synonym of BRAVE.', 'Courageous', 'Timid', 'Weak', 'Fearful', 'A', 'Courageous means showing bravery.', true, now(), now()),
    ('apq_seed_q038', NULL, 'VERBAL', 'easy', 'Choose the antonym of VICTORY.', 'Win', 'Success', 'Triumph', 'Defeat', 'D', 'Defeat is the opposite of victory.', true, now(), now()),
    ('apq_seed_q039', NULL, 'VERBAL', 'easy', 'Choose the correctly spelled word.', 'Recieve', 'Receive', 'Receeve', 'Receve', 'B', 'The rule is i before e except after c: receive.', true, now(), now()),
    ('apq_seed_q040', NULL, 'VERBAL', 'medium', 'Fill in the blank: One of the students ___ absent today.', 'are', 'were', 'is', 'have', 'C', 'The subject one is singular, so it takes is.', true, now(), now()),
    ('apq_seed_q041', NULL, 'VERBAL', 'easy', 'What is the plural of CHILD?', 'Children', 'Childs', 'Childes', 'Childrens', 'A', 'Child has the irregular plural children.', true, now(), now()),
    ('apq_seed_q042', NULL, 'VERBAL', 'medium', 'Choose the antonym of GENEROUS.', 'Kind', 'Giving', 'Warm', 'Stingy', 'D', 'Stingy, meaning unwilling to give, is the opposite of generous.', true, now(), now()),
    ('apq_seed_q043', NULL, 'VERBAL', 'easy', 'Choose the synonym of BEGIN.', 'End', 'Commence', 'Stop', 'Close', 'B', 'Commence means to begin.', true, now(), now()),
    ('apq_seed_q044', NULL, 'VERBAL', 'easy', 'Fill in the blank: The book is ___ the table.', 'in', 'at', 'on', 'of', 'C', 'On indicates a surface such as a table.', true, now(), now()),
    ('apq_seed_q045', NULL, 'VERBAL', 'easy', 'Choose the correctly spelled word.', 'Occasion', 'Occassion', 'Ocasion', 'Occassion', 'A', 'The correct spelling is occasion (double c, single s).', true, now(), now());

INSERT INTO aptitude_questions
    (id, campus_id, category, difficulty, prompt, option_a, option_b, option_c, option_d, correct_option, explanation, active, created_at, updated_at)
VALUES
    ('apq_seed_q046', NULL, 'DATA_INTERPRETATION', 'easy', 'A shop sold 20, 30 and 50 units over three days. What is the total number of units sold?', '90', '100', '110', '80', 'B', '20 + 30 + 50 = 100.', true, now(), now()),
    ('apq_seed_q047', NULL, 'DATA_INTERPRETATION', 'easy', 'Sales over four months were 100, 150, 200 and 250. What is the average monthly sales?', '150', '200', '175', '160', 'C', 'Sum 700 divided by 4 = 175.', true, now(), now()),
    ('apq_seed_q048', NULL, 'DATA_INTERPRETATION', 'easy', 'Out of 500 students, 60% passed. How many students passed?', '300', '250', '350', '200', 'A', '0.60 x 500 = 300.', true, now(), now()),
    ('apq_seed_q049', NULL, 'DATA_INTERPRETATION', 'medium', '25% of a budget of 80000 rupees is spent on rent. What is the rent (in rupees)?', '15000', '25000', '18000', '20000', 'D', '0.25 x 80000 = 20000.', true, now(), now()),
    ('apq_seed_q050', NULL, 'DATA_INTERPRETATION', 'medium', 'Profit rose from 200 to 250. What is the percentage increase?', '20', '25', '50', '30', 'B', 'Increase 50 on 200 = 25%.', true, now(), now()),
    ('apq_seed_q051', NULL, 'DATA_INTERPRETATION', 'medium', 'In a class of 40 with a boys to girls ratio of 2:3, how many girls are there?', '16', '20', '24', '18', 'C', '40 divided by 5 parts = 8; girls = 3 x 8 = 24.', true, now(), now()),
    ('apq_seed_q052', NULL, 'DATA_INTERPRETATION', 'easy', 'Three items cost 50, 70 and 80 rupees. What is their total cost (in rupees)?', '200', '190', '210', '180', 'A', '50 + 70 + 80 = 200.', true, now(), now()),
    ('apq_seed_q053', NULL, 'DATA_INTERPRETATION', 'easy', '40 out of 200 items are defective. What percentage is defective?', '15', '25', '10', '20', 'D', '40 divided by 200 = 20%.', true, now(), now()),
    ('apq_seed_q054', NULL, 'DATA_INTERPRETATION', 'medium', 'A population of 12000 grows by 10% in a year. What is the new population?', '12100', '13200', '13000', '12200', 'B', '12000 x 1.1 = 13200.', true, now(), now()),
    ('apq_seed_q055', NULL, 'DATA_INTERPRETATION', 'easy', 'Monthly expense is 15000 and savings are 5000. What is the monthly income (in rupees)?', '18000', '25000', '20000', '10000', 'C', 'Income = expense + savings = 15000 + 5000 = 20000.', true, now(), now()),
    ('apq_seed_q056', NULL, 'DATA_INTERPRETATION', 'easy', 'Three categories have shares of 30%, 45% and 25%. Which is the largest share?', '45', '30', '25', '35', 'A', '45% is the largest of the three shares.', true, now(), now()),
    ('apq_seed_q057', NULL, 'DATA_INTERPRETATION', 'medium', 'Revenue is 1000 and cost is 750. What is the profit margin percent?', '20', '30', '15', '25', 'D', 'Profit 250 on revenue 1000 = 25%.', true, now(), now()),
    ('apq_seed_q058', NULL, 'DATA_INTERPRETATION', 'easy', 'What is the average of five test scores 60, 70, 80, 90 and 100?', '75', '80', '85', '70', 'B', 'Sum 400 divided by 5 = 80.', true, now(), now()),
    ('apq_seed_q059', NULL, 'DATA_INTERPRETATION', 'easy', '200 units are sold at 5 rupees each. What is the total sales value (in rupees)?', '900', '1100', '1000', '950', 'C', '200 x 5 = 1000.', true, now(), now()),
    ('apq_seed_q060', NULL, 'DATA_INTERPRETATION', 'medium', 'A value of 20 is part of a total of 60. What fraction of the total is it?', 'one-third', 'one-half', 'one-fourth', 'two-thirds', 'A', '20 divided by 60 = one-third.', true, now(), now());
