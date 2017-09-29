--
-- PostgreSQL database dump
--

-- Dumped from database version 9.5.3
-- Dumped by pg_dump version 9.5.3

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: repos; Type: TABLE; Schema: public; Owner: pottery
--

CREATE TABLE repos (
    repoid character varying(255) NOT NULL,
    taskid character varying(255) NOT NULL,
    using_testing_version boolean DEFAULT true NOT NULL,
    expirydate timestamp with time zone
    remote character varying(255) DEFAULT '' NOT NULL,
);


ALTER TABLE repos OWNER TO pottery;

--
-- Name: submissions; Type: TABLE; Schema: public; Owner: pottery
--

CREATE TABLE submissions (
    repoid character varying(255) NOT NULL,
    tag character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    compilationoutput text,
    compilationtimems bigint DEFAULT '-1'::integer NOT NULL,
    harnesstimems bigint DEFAULT '-1'::integer NOT NULL,
    validatortimems bigint DEFAULT '-1'::integer NOT NULL,
    waittimems bigint DEFAULT '-1'::integer NOT NULL,
    errormessage text,
    teststeps text,
    datescheduled timestamp without time zone,
    interpretation character varying(255)
);


ALTER TABLE submissions OWNER TO pottery;

--
-- Name: tasks; Type: TABLE; Schema: public; Owner: pottery
--

CREATE TABLE tasks (
    taskid character varying(255) NOT NULL,
    registeredtag character varying(255),
    retired boolean DEFAULT false NOT NULL,
    testingcopyid character varying(255),
    registeredcopyid character varying(255),
    remote character varying(255) not null default ''
);


ALTER TABLE tasks OWNER TO pottery;

--
-- Name: repos_pkey; Type: CONSTRAINT; Schema: public; Owner: pottery
--

ALTER TABLE ONLY repos
    ADD CONSTRAINT repos_pkey PRIMARY KEY (repoid);


--
-- Name: submissions_pkey; Type: CONSTRAINT; Schema: public; Owner: pottery
--

ALTER TABLE ONLY submissions
    ADD CONSTRAINT submissions_pkey PRIMARY KEY (repoid, tag);


--
-- Name: tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: pottery
--

ALTER TABLE ONLY tasks
    ADD CONSTRAINT tasks_pkey PRIMARY KEY (taskid);


--
-- Name: public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- Name: repos; Type: ACL; Schema: public; Owner: pottery
--

REVOKE ALL ON TABLE repos FROM PUBLIC;
REVOKE ALL ON TABLE repos FROM pottery;
GRANT ALL ON TABLE repos TO pottery;
GRANT ALL ON TABLE repos TO postgres;


--
-- PostgreSQL database dump complete
--

