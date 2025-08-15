import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Database, Eye, EyeOff, Mail, Lock, User, Check } from 'lucide-react';

const SignupPage = () => {
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [formData, setFormData] = useState({
    username: '',
    email: '',
    password: '',
    confirmPassword: ''
  });
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    try {
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          username: formData.username,
          email: formData.email,
          password: formData.password,
          action: 'REGISTER'
        }),
      });

      const data = await response.json();
      
      if (response.ok) {
        console.log('Registration successful:', data);
        alert('Registration successful! Please login with your credentials.');
        navigate('/login');
      } else {
        console.error('Registration failed:', data);
        // Handle error - you might want to show an error message to the user
        alert(data.message || 'Registration failed');
      }
    } catch (error) {
      console.error('Registration error:', error);
      alert('Registration failed. Please try again.');
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value, type, checked } = e.target;
    setFormData({
      ...formData,
      [name]: type === 'checkbox' ? checked : value
    });
  };

  const isPasswordValid = formData.password.length >= 8;
  const isPasswordMatch = formData.password === formData.confirmPassword;
  const isFormValid = formData.username && formData.email && 
                     isPasswordValid && isPasswordMatch;

  return (
    <div className="min-h-screen" style={{ 
      background: 'linear-gradient(135deg, #667EEA 0%, #764BA2 100%)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '20px',
      minHeight: '100vh'
    }}>
      <div className="card" style={{ 
        maxWidth: '500px', 
        width: '100%',
        padding: '48px 32px'
      }}>
        <div style={{ textAlign: 'center', marginBottom: '32px' }}>
          <Link to="/" style={{ 
            display: 'inline-flex', 
            alignItems: 'center', 
            gap: '8px',
            textDecoration: 'none',
            color: 'var(--primary-purple)',
            fontWeight: 'var(--font-weight-bold)',
            fontSize: 'var(--font-size-xl)',
            marginBottom: '8px'
          }}>
            <Database size={32} />
            dbVybe
          </Link>
          <h1 style={{ 
            fontSize: 'var(--font-size-2xl)', 
            fontWeight: 'var(--font-weight-bold)',
            marginBottom: '8px'
          }}>
            Create your account
          </h1>
          <p style={{ color: 'var(--neutral-gray)' }}>
            Start exploring your databases with AI-powered insights
          </p>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="username" className="form-label">
              Username
            </label>
            <div style={{ position: 'relative' }}>
              <User size={20} style={{ 
                position: 'absolute', 
                left: '12px', 
                top: '50%', 
                transform: 'translateY(-50%)',
                color: 'var(--neutral-gray)'
              }} />
              <input
                type="text"
                id="username"
                name="username"
                value={formData.username}
                onChange={handleChange}
                className="form-input"
                style={{ paddingLeft: '44px' }}
                placeholder="Choose a username"
                required
              />
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="email" className="form-label">
              Email address
            </label>
            <div style={{ position: 'relative' }}>
              <Mail size={20} style={{ 
                position: 'absolute', 
                left: '12px', 
                top: '50%', 
                transform: 'translateY(-50%)',
                color: 'var(--neutral-gray)'
              }} />
              <input
                type="email"
                id="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                className="form-input"
                style={{ paddingLeft: '44px' }}
                placeholder="Enter your email"
                required
              />
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="password" className="form-label">
              Password
            </label>
            <div style={{ position: 'relative' }}>
              <Lock size={20} style={{ 
                position: 'absolute', 
                left: '12px', 
                top: '50%', 
                transform: 'translateY(-50%)',
                color: 'var(--neutral-gray)'
              }} />
              <input
                type={showPassword ? 'text' : 'password'}
                id="password"
                name="password"
                value={formData.password}
                onChange={handleChange}
                className="form-input"
                style={{ paddingLeft: '44px', paddingRight: '44px' }}
                placeholder="Create a password"
                required
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                style={{
                  position: 'absolute',
                  right: '12px',
                  top: '50%',
                  transform: 'translateY(-50%)',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  color: 'var(--neutral-gray)'
                }}
              >
                {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
              </button>
            </div>
            {formData.password && (
              <div style={{ 
                marginTop: '8px', 
                fontSize: 'var(--font-size-sm)',
                color: isPasswordValid ? 'var(--semantic-success)' : 'var(--semantic-error)'
              }}>
                {isPasswordValid ? (
                  <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <Check size={16} />
                    Password is strong
                  </span>
                ) : (
                  'Password must be at least 8 characters long'
                )}
              </div>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="confirmPassword" className="form-label">
              Confirm password
            </label>
            <div style={{ position: 'relative' }}>
              <Lock size={20} style={{ 
                position: 'absolute', 
                left: '12px', 
                top: '50%', 
                transform: 'translateY(-50%)',
                color: 'var(--neutral-gray)'
              }} />
              <input
                type={showConfirmPassword ? 'text' : 'password'}
                id="confirmPassword"
                name="confirmPassword"
                value={formData.confirmPassword}
                onChange={handleChange}
                className="form-input"
                style={{ paddingLeft: '44px', paddingRight: '44px' }}
                placeholder="Confirm your password"
                required
              />
              <button
                type="button"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                style={{
                  position: 'absolute',
                  right: '12px',
                  top: '50%',
                  transform: 'translateY(-50%)',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  color: 'var(--neutral-gray)'
                }}
              >
                {showConfirmPassword ? <EyeOff size={20} /> : <Eye size={20} />}
              </button>
            </div>
            {formData.confirmPassword && (
              <div style={{ 
                marginTop: '8px', 
                fontSize: 'var(--font-size-sm)',
                color: isPasswordMatch ? 'var(--semantic-success)' : 'var(--semantic-error)'
              }}>
                {isPasswordMatch ? (
                  <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <Check size={16} />
                    Passwords match
                  </span>
                ) : (
                  'Passwords do not match'
                )}
              </div>
            )}
          </div>

          <button 
            type="submit" 
            className="btn btn-primary" 
            style={{ width: '100%' }}
            disabled={!isFormValid}
          >
            Create account
          </button>
        </form>

        <div style={{ 
          textAlign: 'center', 
          marginTop: '32px',
          paddingTop: '24px',
          borderTop: '1px solid var(--neutral-light-gray)'
        }}>
          <p style={{ color: 'var(--neutral-gray)', marginBottom: '16px' }}>
            Already have an account?{' '}
            <Link to="/login" style={{ 
              color: 'var(--primary-purple)',
              textDecoration: 'none',
              fontWeight: 'var(--font-weight-medium)'
            }}>
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default SignupPage; 