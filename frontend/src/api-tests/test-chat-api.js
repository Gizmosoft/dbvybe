// Using built-in fetch API (available in Node.js 18+)
// Remove the require statement and use fetch directly

const BASE_URL = 'http://localhost:8081';
const CONNECTION_ID = 'c145be6d-6669-49fe-a692-fefa91c4d528';
const SESSION_ID = 'd2615414-97c2-4b5e-b895-d5c12e740f09';
const USER_ID = '736cd67a-19d2-4ff1-9d6e-b6da374099df'; // You can replace with actual user ID

const testQuestions = [
    // Simple data requests
    "List out the entries in the payment table with amount more than 20",
    "What are all the tables in this database?",
    "Show me all customers",
    "Get the database name",
    
    // Analytical questions
    "How many orders are there?",
    "What is the total amount of all payments?",
    "Which customer has the most orders?",
    "Show me payments above $50",
    
    // Schema questions
    "What columns are in the orders table?",
    "Describe the structure of the customers table",
    "What data types are used in the payments table?",
    
    // Complex queries
    "Show me orders with their customer names",
    "Get all payments with customer information",
    "Find customers who have made payments over $100",
    
    // SQL query requests
    "Give me SQL query to get all employees",
    "Write SQL to find customers with more than 5 orders",
    "Show me the SQL query for getting total sales by customer",
    
    // Error cases
    "Get data from non_existent_table",
    "Show me payments from invalid_table",
    "List all records from table_that_does_not_exist"
];

async function testChatAPI(question, testNumber) {
    const requestBody = {
        message: question,
        sessionId: SESSION_ID,
        connectionId: CONNECTION_ID
    };

    console.log(`\n${'='.repeat(80)}`);
    console.log(`TEST ${testNumber}: ${question}`);
    console.log(`${'='.repeat(80)}`);
    
    try {
        const response = await fetch(`${BASE_URL}/api/chat/database`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-User-ID': USER_ID
            },
            body: JSON.stringify(requestBody)
        });

        const data = await response.json();
        
        console.log('Status:', response.status);
        console.log('Response Type:', data.responseType);
        console.log('Error:', data.error);
        console.log('\nQuery Generated:');
        console.log(data.query || 'No query generated');
        console.log('\nResponse:');
        console.log(data.response || 'No response');
        
        if (data.data) {
            console.log('\nData Structure:');
            console.log('- Column Names:', data.data.columnNames || 'N/A');
            console.log('- Row Count:', data.data.rows ? data.data.rows.length : 'N/A');
            console.log('- Sample Data:', data.data.rows ? data.data.rows.slice(0, 3) : 'N/A');
        }
        
        if (data.metadata) {
            console.log('\nMetadata:');
            console.log(data.metadata);
        }

    } catch (error) {
        console.error('Error testing API:', error.message);
    }
}

async function runAllTests() {
    console.log('Starting Chat API Tests...');
    console.log(`Base URL: ${BASE_URL}`);
    console.log(`Connection ID: ${CONNECTION_ID}`);
    console.log(`Session ID: ${SESSION_ID}`);
    console.log(`User ID: ${USER_ID}`);
    
    for (let i = 0; i < testQuestions.length; i++) {
        await testChatAPI(testQuestions[i], i + 1);
        
        // Add a small delay between requests
        await new Promise(resolve => setTimeout(resolve, 1000));
    }
    
    console.log('\n' + '='.repeat(80));
    console.log('ALL TESTS COMPLETED');
    console.log('='.repeat(80));
}

// Run the tests
runAllTests().catch(console.error);
