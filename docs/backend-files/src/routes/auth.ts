import type { FastifyPluginAsync, FastifyRequest } from "fastify";
import { z } from "zod";
import { authenticate } from "../auth/context.js";
import { AppError, parse } from "../utils/errors.js";
import { hashPassword, verifyPassword } from "../utils/security.js";
import { logActivity } from "../services/activity.js";
import { randomInt, randomUUID } from "node:crypto";
import { sha256 } from "../utils/security.js";

const password = z
  .string()
  .min(8, "Password must be at least 8 characters")
  .max(128)
  .regex(/[A-Za-z]/, "Password must contain a letter")
  .regex(/\d/, "Password must contain a digit");

const registerSchema = z.object({
  firstName: z.string().trim().min(1).max(60),
  secondName: z.string().trim().min(1).max(60),
  studentId: z.string().trim().min(3).max(40), // index number
  email: z.string().trim().toLowerCase().email().max(200),
  departmentId: z.string().min(1).max(64),
  stream: z.enum(["REGULAR", "WEEKLY", "EVENING"]).default("REGULAR"),
  password,
});
const forgotSchema = z.object({ email: z.string().trim().toLowerCase().email() });
const resetSchema = z.object({
  email: z.string().trim().toLowerCase().email(),
  code: z.string().trim().regex(/^\d{6}$/, "Enter the 6-digit code from the email"),
  newPassword: password,
});
/** Wrong-code counter per user (in memory, single server). 5 wrong tries burn the code. */
const MAX_CODE_TRIES = 5;
const codeTries = new Map<string, number>();

const loginSchema = z.object({
  email: z.string().trim().toLowerCase().email(),
  password: z.string().min(1).max(128),
});

const changePasswordSchema = z.object({
  currentPassword: z.string().min(1).max(128),
  newPassword: password,
});

export function publicUser(u: {
  id: string; email: string; fullName: string; role: string; mustChangePassword: boolean;
  studentProfile?: { studentId: string; departmentId: string | null; semester: number | null; stream?: string } | null;
  lecturerProfile?: { staffId: string; departmentId: string | null } | null;
}) {
  return {
    id: u.id,
    email: u.email,
    fullName: u.fullName,
    role: u.role,
    mustChangePassword: u.mustChangePassword,
    student: u.studentProfile
      ? { studentId: u.studentProfile.studentId, departmentId: u.studentProfile.departmentId, semester: u.studentProfile.semester, stream: u.studentProfile.stream }
      : undefined,
    lecturer: u.lecturerProfile
      ? { staffId: u.lecturerProfile.staffId, departmentId: u.lecturerProfile.departmentId }
      : undefined,
  };
}

export const authRoutes: FastifyPluginAsync = async (app) => {
  const db = app.prisma;
  const cfg = app.appConfig;
  /**
   * Per-route throttles, bucketed by real client IP (see `req.clientIp` in app.ts) so a forged
   * X-Forwarded-For header cannot buy a fresh bucket, and so one abusive address cannot lock a
   * legitimate user out. Tests run with a very high ceiling.
   */
  const limiter = (max: number, timeWindow: string) => ({
    config: {
      rateLimit: {
        max: cfg.NODE_ENV === "test" ? 10_000 : max,
        timeWindow,
        keyGenerator: (req: FastifyRequest) => req.clientIp || req.ip,
      },
    },
  });
  /** Password guessing: 10 attempts per 15 minutes per address. */
  const loginLimit = limiter(10, "15 minutes");
  /** Account farming: 20 sign-ups per hour per address. */
  const registerLimit = limiter(20, "1 hour");
  /** Reset codes are 6 digits, so minting them is expensive by design: 5 per 15 minutes. */
  const resetRequestLimit = limiter(5, "15 minutes");
  /** Enough to retype a code, and the per-account 5-wrong-tries counter still applies. */
  const resetConfirmLimit = limiter(10, "15 minutes");
  const passwordChangeLimit = limiter(10, "15 minutes");

  const issue = (user: { id: string; role: string }) =>
    app.jwt.sign({ sub: user.id, role: user.role, jti: randomUUID() });

  app.post("/register/student", registerLimit, async (req, reply) => {
    const body = parse(registerSchema, req.body);
    const clash = await db.user.findUnique({ where: { email: body.email } });
    if (clash) throw new AppError(409, "EMAIL_TAKEN", "An account with this email already exists");
    const idClash = await db.studentProfile.findUnique({ where: { studentId: body.studentId } });
    if (idClash) throw new AppError(409, "STUDENT_ID_TAKEN", "This student ID is already registered");

    const dept = await db.department.findUnique({ where: { id: body.departmentId } });
    if (!dept) throw new AppError(404, "DEPARTMENT_NOT_FOUND", "Department not found");

    const user = await db.user.create({
      data: {
        email: body.email,
        fullName: `${body.firstName} ${body.secondName}`,
        role: "STUDENT",
        passwordHash: await hashPassword(body.password, cfg.BCRYPT_ROUNDS),
        studentProfile: { create: { studentId: body.studentId, departmentId: dept.id, stream: body.stream } },
      },
      include: { studentProfile: true },
    });
    await logActivity(db, { userId: user.id, role: "STUDENT" }, "student.registered", { studentId: body.studentId });
    return reply.status(201).send({ token: issue(user), user: publicUser(user) });
  });

  app.post("/login", loginLimit, async (req) => {
    const body = parse(loginSchema, req.body);
    const user = await db.user.findUnique({
      where: { email: body.email },
      include: { studentProfile: true, lecturerProfile: true },
    });
    // Always run a hash comparison so response time does not reveal whether the email exists.
    const ok = await verifyPassword(
      body.password,
      user?.passwordHash ?? "$2a$12$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidin",
    );
    if (!user || !ok || !user.isActive) throw new AppError(401, "INVALID_CREDENTIALS", "Incorrect email or password");
    await db.user.update({ where: { id: user.id }, data: { lastLoginAt: new Date() } });
    await logActivity(db, { userId: user.id, role: user.role }, "auth.login");
    return { token: issue(user), user: publicUser(user) };
  });

  // Password reset by e-mailed 6-digit code. The response is identical whether or not the account exists.
  const codeHash = (userId: string, code: string) => sha256(`${userId}:${code}`);

  app.post("/forgot-password", resetRequestLimit, async (req) => {
    const body = parse(forgotSchema, req.body);
    const user = await db.user.findUnique({ where: { email: body.email } });
    if (user && user.isActive) {
      const code = String(randomInt(0, 1_000_000)).padStart(6, "0");
      await db.passwordReset.updateMany({ where: { userId: user.id, usedAt: null }, data: { usedAt: new Date() } }); // older codes stop working
      await db.passwordReset.create({
        data: { userId: user.id, tokenHash: codeHash(user.id, code), expiresAt: new Date(Date.now() + cfg.PASSWORD_RESET_TTL_MINUTES * 60_000) },
      });
      codeTries.delete(user.id);
      if (app.mailer.configured) {
        try {
          await app.mailer.send({
            to: user.email,
            subject: `Your password reset code: ${code}`,
            text: `Hello ${user.fullName},\n\nYour password reset code is: ${code}\n\nEnter it on the reset page to choose a new password. It is valid for ${cfg.PASSWORD_RESET_TTL_MINUTES} minutes and works once.\n\nIf you did not ask for this, ignore this email and your password stays the same.`,
          });
        } catch (e) {
          req.log.error({ err: (e as Error).message }, "password reset email failed");
        }
      }
      await logActivity(db, { userId: user.id, role: user.role }, "auth.password.reset_requested");
    }
    return { ok: true, message: "If that email has an account, a 6-digit code has been sent." };
  });

  app.post("/reset-password", resetConfirmLimit, async (req) => {
    const body = parse(resetSchema, req.body);
    const bad = () => new AppError(400, "INVALID_RESET_CODE", "This code is wrong or has expired. Request a new one.");
    const user = await db.user.findUnique({ where: { email: body.email } });
    if (!user || !user.isActive) throw bad();
    const rec = await db.passwordReset.findFirst({ where: { userId: user.id, usedAt: null, expiresAt: { gt: new Date() } }, orderBy: { createdAt: "desc" } });
    if (!rec) throw bad();
    const tries = codeTries.get(user.id) ?? 0;
    if (tries >= MAX_CODE_TRIES) {
      await db.passwordReset.update({ where: { id: rec.id }, data: { usedAt: new Date() } });
      throw bad();
    }
    if (rec.tokenHash !== codeHash(user.id, body.code)) { codeTries.set(user.id, tries + 1); throw bad(); }
    codeTries.delete(user.id);
    await db.$transaction([
      db.user.update({ where: { id: user.id }, data: { passwordHash: await hashPassword(body.newPassword, cfg.BCRYPT_ROUNDS), mustChangePassword: false } }),
      db.passwordReset.update({ where: { id: rec.id }, data: { usedAt: new Date() } }),
    ]);
    await logActivity(db, { userId: user.id }, "auth.password.reset_completed");
    return { ok: true };
  });

  app.post("/logout", { preHandler: authenticate }, async (req) => {
    const a = req.auth;
    const expiresAt = a.tokenExp ? new Date(a.tokenExp * 1000) : new Date(Date.now() + 24 * 3600_000);
    await db.revokedToken.upsert({ where: { jti: a.jti }, create: { jti: a.jti, expiresAt }, update: {} });
    await db.revokedToken.deleteMany({ where: { expiresAt: { lt: new Date() } } });
    await logActivity(db, a, "auth.logout");
    return { ok: true };
  });

  app.get("/me", { preHandler: authenticate }, async (req) => {
    const user = await db.user.findUniqueOrThrow({
      where: { id: req.auth.userId },
      include: { studentProfile: { include: { department: true } }, lecturerProfile: { include: { department: true } } },
    });
    return {
      ...publicUser(user),
      department: user.studentProfile?.department ?? user.lecturerProfile?.department ?? null,
    };
  });

  app.post("/change-password", { preHandler: authenticate, ...passwordChangeLimit }, async (req) => {
    const body = parse(changePasswordSchema, req.body);
    const user = await db.user.findUniqueOrThrow({ where: { id: req.auth.userId } });
    if (!(await verifyPassword(body.currentPassword, user.passwordHash))) {
      throw new AppError(401, "INVALID_CREDENTIALS", "Current password is incorrect");
    }
    await db.user.update({
      where: { id: user.id },
      data: { passwordHash: await hashPassword(body.newPassword, cfg.BCRYPT_ROUNDS), mustChangePassword: false },
    });
    await logActivity(db, req.auth, "auth.password.changed");
    return { ok: true };
  });
};
