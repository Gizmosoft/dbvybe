// Simple test script for chat API
const https = require('https');
const http = require('http');

const BASE_URL = 'http://localhost:8081';
const CONNECTION_ID = 'c145be6d-6669-49fe-a692-fefa91c4d528';
const SESSION_ID = 'd2615414-97c2-4b5e-b895-d5c12e740f09';
const USER_ID = '736cd67a-19d2-4ff1-9d6e-b6da374099df';

const testQuestions = [
    "List out the entries in the payment table with amount more than 20",
    "What are all the tables in this database?",
    "Show me all customers",
    "Get the database name",
    "How many orders are there?",
    "What columns are in the orders table?",
    "Give me SQL query to get all employees"
];

function makeRequest(question, testNumber) {
    const data = JSON.stringify({
        message: question,
        sessionId: SESSION_ID,
        connectionId: CONNECTION_ID
    });

    const options = {
        hostname: 'localhost',
        port: 8081,
        path: '/api/chat/database',
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-User-ID': USER_ID,
            'Content-Length': Buffer.byteLength(data)
        }
    };

    console.log(`\n${'='.repeat(80)}`);
    console.log(`TEST ${testNumber}: ${question}`);
    console.log(`${'='.repeat(80)}`);

    const req = http.request(options, (res) => {
        let responseData = '';
        
        res.on('data', (chunk) => {
            responseData += chunk;
        });
        
        res.on('end', () => {
            try {
                const response = JSON.parse(responseData);
                console.log('Status:', res.statusCode);
                console.log('Response Type:', response.responseType);
                console.log('Error:', response.error);
                console.log('\nQuery Generated:');
                console.log(response.query || 'No query generated');
                console.log('\nResponse:');
                console.log(response.response || 'No response');
                
                if (response.data) {
                    console.log('\nData Structure:');
                    console.log('- Column Names:', response.data.columnNames || 'N/A');
                    console.log('- Row Count:', response.data.rows ? response.data.rows.length : 'N/A');
                    if (response.data.rows && response.data.rows.length > 0) {
                        console.log('- Sample Data:', response.data.rows.slice(0, 2));
                    }
                }
                
                if (response.metadata) {
                    console.log('\nMetadata:', response.metadata);
                }
            } catch (error) {
                console.log('Raw response:', responseData);
                console.error('Error parsing response:', error.message);
            }
        });
    });

    req.on('error', (error) => {
        console.error('Request error:', error.message);
    });

    req.write(data);
    req.end();
}

async function runTests() {
    console.log('Starting Chat API Tests...');
    console.log(`Base URL: ${BASE_URL}`);
    console.log(`Connection ID: ${CONNECTION_ID}`);
    console.log(`Session ID: ${SESSION_ID}`);
    console.log(`User ID: ${USER_ID}`);
    
    for (let i = 0; i < testQuestions.length; i++) {
        makeRequest(testQuestions[i], i + 1);
        
        // Wait between requests
        await new Promise(resolve => setTimeout(resolve, 2000));
    }
    
    console.log('\n' + '='.repeat(80));
    console.log('ALL TESTS COMPLETED');
    console.log('='.repeat(80));
}

// Run tests
runTests().catch(console.error);
