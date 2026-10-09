import { useState } from 'react'
import { FiX } from "react-icons/fi";
import { motion } from "motion/react"
import api from '../utils/axios';
function LoginModel({ onClose ,setUser}) {
    const [isRegistering, setIsRegistering] = useState(false)
    const [name, setName] = useState("")
    const [email, setEmail] = useState("")
    const [password, setPassword] = useState("")
    const [error, setError] = useState("")
    const [loading, setLoading] = useState(false)

    const handleSubmit = async (event) => {
        event.preventDefault()
        setError("")
        setLoading(true)
        try {
            const endpoint = isRegistering ? "/api/auth/register" : "/api/auth/login"
            const payload = isRegistering ? { name, email, password } : { email, password }
            const response = await api.post(endpoint, payload)
            localStorage.setItem("accessToken", response.data.accessToken)
            setUser(response?.data?.user)
            onClose()
        } catch (error) {
            setError(error?.response?.data?.message || "Authentication failed")
        } finally {
            setLoading(false)
        }
    }


    return (
        <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-md px-4'>
            <div className='relative w-full max-w-sm
        bg-[#0A0A0A]/80 backdrop-blur-2xl
        border border-white/10
        rounded-2xl
        overflow-hidden
        shadow-[0_8px_32px_rgba(0,0,0,0.25)]'>

                <div className='absolute inset-0 bg-gradient-to-br from-white/[0.08] via-transparent to-transparent pointer-events-none' />

                <div className='relative p-7'>
                    <button
                        onClick={onClose}
                        className='absolute top-4 right-4
              text-white/30 hover:text-white
              transition-colors'><FiX size={16} /></button>

                    <h2 className='text-lg font-bold text-center mb-2 text-white'>
                        {isRegistering ? "Create your" : "Sign in to"}{" "}
                        <span className='font-extrabold text-lg tracking-tight text-white'>FresherAI</span>
                    </h2>
                    <p className='text-white/45 text-center text-xs'>
                        {isRegistering ? "Start your AI interview journey" : "Continue your AI interview journey"}
                    </p>

                    <form onSubmit={handleSubmit} className='mt-7 space-y-3'>
                        {isRegistering && <input
                            value={name}
                            onChange={(event) => setName(event.target.value)}
                            placeholder='Full name'
                            required
                            maxLength={120}
                            className='w-full rounded-xl border border-white/15 bg-white/10 px-3 py-3 text-sm text-white placeholder:text-white/35 outline-none focus:border-white/40'
                        />}
                        <input
                            type='email'
                            value={email}
                            onChange={(event) => setEmail(event.target.value)}
                            placeholder='Email address'
                            required
                            className='w-full rounded-xl border border-white/15 bg-white/10 px-3 py-3 text-sm text-white placeholder:text-white/35 outline-none focus:border-white/40'
                        />
                        <input
                            type='password'
                            value={password}
                            onChange={(event) => setPassword(event.target.value)}
                            placeholder='Password'
                            required
                            minLength={8}
                            className='w-full rounded-xl border border-white/15 bg-white/10 px-3 py-3 text-sm text-white placeholder:text-white/35 outline-none focus:border-white/40'
                        />
                        {error && <p className='text-xs text-red-300'>{error}</p>}
                        <motion.button
                            type='submit'
                            disabled={loading}
                            whileHover={{ scale: 1.04 }}
                            whileTap={{ scale: 0.97 }}
                            className='w-full flex items-center justify-center gap-3 py-3 rounded-xl border border-white/15 bg-white/10 backdrop-blur-md hover:border-white/25 hover:bg-white/[0.14] shadow-inner transition-all'
                        >
                            <span className='text-white font-medium text-sm'>
                                {loading ? "Please wait..." : isRegistering ? "Create account" : "Sign in"}
                            </span>
                        </motion.button>
                    </form>
                    <button
                        type='button'
                        onClick={() => { setIsRegistering(!isRegistering); setError("") }}
                        className='mt-4 w-full text-xs text-white/55 hover:text-white transition-colors'
                    >
                        {isRegistering ? "Already have an account? Sign in" : "New here? Create an account"}
                    </button>
                </div>

                <div className='relative border-t border-white/10 bg-black/30 p-4 text-center'>
                <p className='text-white/30 text-xs'>
                Secure authentication powered by Spring Security
                </p>
                </div>




            </div>

        </div>
    )
}

export default LoginModel
